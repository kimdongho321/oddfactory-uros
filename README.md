# 우로스·우로롱 밝기와 볼륨

트릭컬 리바이브의 캐릭터를 바탕으로 만든 비공식 Android 팬 프로젝트입니다.

왼쪽 우로롱을 끌어 밝기를, 오른쪽 우로스를 끌어 볼륨을 조절합니다. 두 번 터치하면 엎드리거나 깨어나고, 세 번 터치하면 설정·종료 메뉴를 엽니다.

이 소스 빌드에는 반복 혀 애니메이션이 빠져 있습니다. 밝기·볼륨과 엎드리기·깨우기 코드는 포함되어 있습니다.

## 실행

Android 11 이상에서 앱을 열고 접근성 설정에서 우로스 서비스를 허용한 뒤 실행을 누릅니다. 밝기 조작은 앱 상세 설정에서 시스템 설정 변경 권한도 허용합니다.
설치 및 초기 권한 설정 후 앱에서 실행과 재실행을 할 수 있습니다.

## 빌드

JDK 21, Android SDK Platform 35와 Build Tools 34.0.0이 필요합니다. Android Studio로 이 폴더를 열거나, 터미널에서 JDK와 Android SDK 환경을 설정하고 빌드합니다.

    .\gradlew.bat :app:assembleDebug

macOS·Linux에서는 다음 명령을 사용합니다.

    sh ./gradlew :app:assembleDebug

디버그 APK는 app/build/outputs/apk/debug/app-debug.apk에 생성됩니다. Android는 앱 서명이 다른 기존 설치 위에 디버그 APK를 설치할 수 없습니다.

## 권리 및 고지

캐릭터의 원작 IP는 에피드게임즈에 있습니다. 앱의 팬 아트에는 에피드게임즈의 [트릭컬 2차 창작 가이드라인](https://epidgames.oqupie.com/portal/2399/article/73315)이 적용됩니다.
설정 UI의 메이플스토리 서체는 넥슨이 제공했습니다. [서체 사용 조건](app/src/main/assets/licenses/maplestory.txt)을 확인할 수 있습니다. Gradle Wrapper의 [라이선스](gradle/LICENSE)와 [고지](gradle/NOTICE)는 별도로 제공됩니다.
