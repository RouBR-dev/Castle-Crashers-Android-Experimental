# Castle Crashers Android — build arm64

Este pacote Gradle contém os assets de Castle Crashers em `app/src/main/assets/game_data` e as bibliotecas nativas pré-compiladas para `arm64-v8a` em `app/src/main/jniLibs/arm64-v8a`. Não é necessário obter arquivos de jogo separadamente.

## Requisitos

- JDK 21
- Android SDK com Platform 35 e Build Tools 34.0.0 ou compatíveis
- Espaço livre para o APK e para a primeira instalação dos dados do jogo (aprox. 140 MB extraídos no armazenamento privado do app)

## Compilar

Na pasta extraída deste projeto, execute:

```bash
./gradlew --no-daemon assembleDebug
```

O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`. O APK é debug-assinado e contém somente a ABI `arm64-v8a`; não é um build release assinado para distribuição na Play Store.

Na primeira inicialização, o app copia os dados embutidos para o armazenamento privado do aplicativo. Essa cópia pode levar alguns instantes e requer aproximadamente 140 MB livres. A execução em aparelho físico não foi validada nesta sessão.

## Diagnóstico de falhas

O app grava os logs em dois locais. O caminho app-specific externo, mais fácil de localizar no Android Studio, é `Android/data/com.castlecrashers.recomp/files/logs/` (Device Explorer normalmente mostra `sdcard/Android/data/com.castlecrashers.recomp/files/logs/`): `bootstrap.log` registra processo/Application antes da Activity, `fatal.log` recebe exceções não capturadas, `logcat.log` captura mensagens do processo e `native.log` recebe stdout/stderr nativos. Há cópias adicionais no armazenamento interno em `data/user/0/com.castlecrashers.recomp/files/logs/`: `app.log`, `crash.log` e `bootstrap.log`. Não é necessária permissão de armazenamento. Também é possível obter logcat ao vivo via `adb logcat --pid=$(adb shell pidof -s com.castlecrashers.recomp) -v threadtime` enquanto o processo está ativo.

Ao abrir o ícone, `SetupActivity` exibe opções pré-jogo antes da SDL. Para usar Turnip, extraia o pacote ARM64 do driver e selecione a pasta em Downloads; ela precisa conter `libvulkan_freedreno.so` e os arquivos auxiliares do mesmo pacote. O app copia os arquivos escolhidos para `files/turnip-driver/` e configura o AdrenoTools para carregá-los no início do Vulkan. O Android não permite `dlopen` do driver diretamente do armazenamento compartilhado/Downloads, por isso a cópia para a pasta privada é necessária. A seleção “Driver do sistema” continua disponível como fallback manual. O APK não distribui Turnip: o usuário fornece o arquivo. Uma biblioteca de driver de terceiros pode ser incompatível com o aparelho ou com a versão do runtime; se isso ocorrer, escolha o driver do sistema.

O build Android Vulkan agora integra a dependência upstream AdrenoTools vendorizada em `third_party/adrenotools`, ativa o código custom-loader e copia `libhook_impl.so` e `libmain_hook.so` para `jniLibs/arm64-v8a`. Para recompilar o nativo com NDK, configure `ANDROID_NDK_ROOT` e execute `cmake --preset android-release` e `cmake --build --preset android-release --target castle --parallel 4`; depois execute Gradle para empacotar o APK.

O log fornecido anteriormente não registra uma exceção ou sinal fatal: ele confirma que o backend Vulkan abriu, criou swapchain e retomou a thread principal do jogo, mas a imagem continuou preta. Também mostra tentativas de abrir `shaders/SobelEdge.xpu` e `shaders/VertexColorFillPS.xpu`, nomes ausentes do conjunto de arquivos observado. A integração Turnip oferece uma alternativa de driver para teste, mas não prova nem corrige por si só a falha de inicialização/renderização do jogo. Não há confirmação em aparelho físico nesta sessão.
