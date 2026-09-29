#!/usr/bin/env python3
"""本地 release 打包脚本（build-release.ps1 的 Python 版）。

用法::

    python scripts/build_release.py                                  # 默认打 :mobile:assembleRelease
    python scripts/build_release.py :mobile:lintRelease :mobile:assembleRelease
    python scripts/build_release.py :automotive:assembleRelease
    python scripts/build_release.py --dry-run                        # 只打印解析结果，不构建

签名凭据**不写在本文件里**，因为 scripts/*.py 会被提交进公开仓库。取值顺序：

1. 环境变量 ``ANDROID_KEYSTORE_PATH`` / ``ANDROID_KEYSTORE_PASSWORD`` /
   ``ANDROID_KEY_ALIAS`` / ``ANDROID_KEY_PASSWORD``；
2. 仓库根的 ``release-signing.properties``（被 .gitignore 忽略），键名：
   ``keystorePath`` / ``keystorePassword`` / ``keyAlias`` / ``keyPassword``
   （路径写正斜杠即可，例如 ``E:/WorkTool/keys/diplay-release.jks``）。

凭据文件**不能放进 ``.private/``**：那里是 ``DIPLAY_AUTH_ASSETS_DIR``，整个目录会被
并入 APK assets，明文口令会随包发出去（``rejectBundledCredentials`` 只拦
``.pk8/.p7b/.jks`` 之类，拦不住 ``.properties``）。脚本启动前会检查这一点并拒绝构建。

运行身份：若 ``.private/offline-mfi/{identity.pk8,certificate.p7b}`` 存在，则把
``DIPLAY_AUTH_ASSETS_DIR`` 指向仓库根的 ``.private``，让凭据被打进 APK assets。
脚本必须留在 ``scripts/``：放进 ``.private/`` 会被 Gradle 当成 assets 一起打包，
连脚本里的签名口令一起发出去。

构建前会先停掉 Gradle 守护进程——守护进程不会感知到新设置的环境变量。
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_TASKS = (":mobile:assembleRelease",)
DEFAULT_CREDENTIALS = REPO_ROOT / "release-signing.properties"
DEFAULT_AUTH_ASSETS = REPO_ROOT / ".private"
FALLBACK_SDK = "E:/Android/Sdk"

ENV_KEYSTORE_PATH = "ANDROID_KEYSTORE_PATH"
ENV_KEYSTORE_PASSWORD = "ANDROID_KEYSTORE_PASSWORD"
ENV_KEY_ALIAS = "ANDROID_KEY_ALIAS"
ENV_KEY_PASSWORD = "ANDROID_KEY_PASSWORD"


class BuildError(Exception):
    """Anything that stops the build before Gradle is started."""


def read_properties(path: Path) -> dict[str, str]:
    """Parse a flat ``key=value`` file; ``#`` starts a comment."""
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip()
    return values


def sdk_dir_from_local_properties() -> str | None:
    """Read ``sdk.dir`` from local.properties, or None when it is absent."""
    local_properties = REPO_ROOT / "local.properties"
    if not local_properties.is_file():
        return None
    for raw in local_properties.read_text(encoding="utf-8-sig").splitlines():
        if raw.strip().startswith("sdk.dir"):
            _, _, value = raw.partition("=")
            # Properties escaping: E\:\\Android\\Sdk -> E:\Android\Sdk
            return value.strip().replace("\\\\", "\\").replace("\\:", ":")
    return None


def resolve_credentials(credentials_path: Path, keystore_override: str | None) -> dict[str, str]:
    """Environment wins over the ignored properties file, which wins over nothing."""
    file_values = read_properties(credentials_path) if credentials_path.is_file() else {}

    keystore = keystore_override or os.environ.get(ENV_KEYSTORE_PATH) or file_values.get("keystorePath")
    if not keystore:
        raise BuildError(
            f"找不到签名库路径：请设置 {ENV_KEYSTORE_PATH}，或在 {credentials_path} 里写 keystorePath"
        )
    if not Path(keystore).is_file():
        raise BuildError(f"找不到签名库文件: {keystore}")

    password = os.environ.get(ENV_KEYSTORE_PASSWORD) or file_values.get("keystorePassword")
    alias = os.environ.get(ENV_KEY_ALIAS) or file_values.get("keyAlias")
    key_password = os.environ.get(ENV_KEY_PASSWORD) or file_values.get("keyPassword")
    if not password or not alias or not key_password:
        raise BuildError(
            f"签名凭据不完整：请在 {credentials_path} 里补齐 keystorePassword / keyAlias / keyPassword"
        )
    return {
        "keystore": keystore,
        "keystorePassword": password,
        "keyAlias": alias,
        "keyPassword": key_password,
    }


def resolve_sdk() -> str | None:
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(name):
            return os.environ[name]
    return sdk_dir_from_local_properties() or FALLBACK_SDK


def resolve_auth_assets(requested: str | None) -> Path | None:
    """The directory Gradle merges into APK assets, or None for an identity-free build."""
    if requested:
        candidate = Path(requested)
    elif os.environ.get("DIPLAY_AUTH_ASSETS_DIR"):
        candidate = Path(os.environ["DIPLAY_AUTH_ASSETS_DIR"])
    else:
        candidate = DEFAULT_AUTH_ASSETS
    required = (candidate / "offline-mfi" / "identity.pk8", candidate / "offline-mfi" / "certificate.p7b")
    return candidate if all(path.is_file() and path.stat().st_size > 0 for path in required) else None


def unexpected_asset_files(auth_assets: Path) -> list[Path]:
    """Files in the auth assets dir that Gradle would ship inside the APK as well.

    Only ``offline-mfi/`` belongs there; anything else (a credentials file, a build
    script) would be packaged and handed out with the APK.
    """
    return sorted(
        path
        for path in auth_assets.rglob("*")
        if path.is_file() and path.relative_to(auth_assets).parts[0] != "offline-mfi"
    )


def gradlew_command() -> list[str]:
    wrapper = REPO_ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise BuildError(f"找不到 Gradle wrapper: {wrapper}")
    if os.name != "nt":
        wrapper.chmod(wrapper.stat().st_mode | 0o111)
    return [str(wrapper)]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="本地 release 打包（签名凭据来自环境变量或被 gitignore 的凭据文件）",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("tasks", nargs="*", default=list(DEFAULT_TASKS),
                        help=f"要执行的 Gradle 任务，默认 {' '.join(DEFAULT_TASKS)}")
    parser.add_argument("--credentials", default=str(DEFAULT_CREDENTIALS),
                        help=f"签名凭据文件，默认 {DEFAULT_CREDENTIALS}")
    parser.add_argument("--keystore", help="覆盖签名库路径")
    parser.add_argument("--auth-assets-dir", help="覆盖运行身份目录（默认自动探测 .private/offline-mfi）")
    parser.add_argument("--allow-extra-assets", action="store_true",
                        help="允许运行身份目录里存在 offline-mfi 之外的文件（会一起打进 APK）")
    parser.add_argument("--dry-run", action="store_true", help="只打印解析结果，不启动 Gradle")
    args = parser.parse_args(argv)

    try:
        credentials = resolve_credentials(Path(args.credentials), args.keystore)
        auth_assets = resolve_auth_assets(args.auth_assets_dir)
        if auth_assets and not args.allow_extra_assets:
            extra = unexpected_asset_files(auth_assets)
            if extra:
                listed = "\n  ".join(str(path) for path in extra)
                raise BuildError(
                    f"运行身份目录 {auth_assets} 里还有会被一起打进 APK 的文件：\n  {listed}\n"
                    f"签名凭据请放在仓库根（{DEFAULT_CREDENTIALS}）。确认要一起打包请加 --allow-extra-assets"
                )
        gradlew = gradlew_command()
    except BuildError as error:
        print(f"错误: {error}", file=sys.stderr)
        return 2

    sdk = resolve_sdk()
    password_from_env = bool(os.environ.get(ENV_KEYSTORE_PASSWORD))

    # The Gradle daemon keeps the environment of the process that started it.
    os.environ[ENV_KEYSTORE_PATH] = credentials["keystore"]
    os.environ[ENV_KEYSTORE_PASSWORD] = credentials["keystorePassword"]
    os.environ[ENV_KEY_ALIAS] = credentials["keyAlias"]
    os.environ[ENV_KEY_PASSWORD] = credentials["keyPassword"]
    if sdk:
        os.environ["ANDROID_HOME"] = sdk
        os.environ["ANDROID_SDK_ROOT"] = sdk
        if not Path(sdk).is_dir():
            print(f"警告: Android SDK 目录不存在: {sdk}", file=sys.stderr)
    if auth_assets:
        os.environ["DIPLAY_AUTH_ASSETS_DIR"] = str(auth_assets)
    else:
        os.environ.pop("DIPLAY_AUTH_ASSETS_DIR", None)

    print(f"仓库根        : {REPO_ROOT}")
    print(f"签名库        : {credentials['keystore']}")
    print(f"签名口令      : {'*' * 8} (来自 {'环境变量' if password_from_env else args.credentials})")
    print(f"Android SDK   : {sdk or '未设置'}")
    print(f"运行身份      : {auth_assets or '未找到 .private/offline-mfi，本次打包不含运行身份'}")
    if not auth_assets:
        print("提示: 不含运行身份的包只能用于源码验证，装到车机上无法完成 CarPlay 认证。", file=sys.stderr)
    print(f"Gradle 任务   : {' '.join(args.tasks)}")

    if args.dry_run:
        print("--dry-run：不执行构建。")
        return 0

    # Stop the daemon so it picks up the environment set above, then build with live output.
    subprocess.run([*gradlew, "--stop"], cwd=REPO_ROOT, check=False)
    completed = subprocess.run([*gradlew, "--console=plain", *args.tasks], cwd=REPO_ROOT, check=False)
    return completed.returncode


if __name__ == "__main__":
    sys.exit(main())
