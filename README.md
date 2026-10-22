# Castle Crashers Android — port experimental ARM64

> **Estado: experimental e ainda não jogável.** A compilação ARM64 e o APK debug foram gerados, mas no aparelho de teste relatado pelo usuário o jogo fecha ou fica com a tela preta. Não trate este projeto como um port funcional.

Este repositório documenta uma tentativa comunitária de executar Castle Crashers no Android usando o runtime **ReXGlue**, código de jogo recompilado de PPC para C++, SDL3 e Vulkan. **Não é um port nativo do jogo, não é um emulador Wine/Proton e não reutiliza o renderizador nativo do Skate 3.** O uso do mesmo SDK e de partes da estrutura Android do Skate 3 não transfere automaticamente compatibilidade entre os jogos.

## Plataforma e versão da origem

- **Plataforma original:** Xbox 360, distribuição Xbox LIVE Arcade (XBLA).
- **Formato recebido:** conteúdo Xbox 360 em estrutura de pacote **STFS** dentro de `584108B7.zip`.
- **ISO:** nenhuma ISO foi fornecida ou usada. Chamar esta origem de “versão da ISO” seria incorreto.
- **Title ID:** `0x584108B7` (o diretório/pacote usa o identificador `584108B7`).
- **Execution ID do `default.xex`:** Media ID `1DF2F0B2`; versão `0x00000103` (1.03) e base version `0x00000103`. Este é o valor lido no cabeçalho XEX; não foi confirmado como rótulo comercial de uma revisão/ISO.
- **Metadados de `ArcadeInfo.xml`:** `xdkVersion="2.0.7645.0"` e `projectVersion="1.0.544.0"`. São metadados do XDK/projeto, não a versão comercial do jogo.
- **Destino Android:** `arm64-v8a`; APK `versionName` 0.1.0, `minSdk` 33 e `targetSdk` 35, compilado com NDK 27.2.12479018.
- **Aparelho-alvo relatado:** Samsung S20 FE, Snapdragon 865/Adreno 650, Android 13. A execução física ainda não foi validada pelos mantenedores.

## O que foi aplicado

- Base Android com SDL3, runtime ReXGlue e backend Vulkan; codegen do `default.xex` para fontes C++ geradas.
- Empacotamento dos dados fornecidos no APK de teste e cópia inicial para o diretório privado do app.
- Tela pré-jogo com opções de driver Vulkan do sistema ou Turnip, GPU, resolução, modo de apresentação e quadros em voo.
- Integração experimental de AdrenoTools e hooks ARM64 para permitir que o usuário selecione uma pasta Turnip compatível. O app importa a pasta escolhida para o armazenamento privado. **O APK não contém o driver Turnip**; o usuário fornece um pacote compatível.
- Captura de logs de inicialização, logcat do processo, saída nativa e exceções para os diretórios de logs do app.
- Build `assembleDebug` e binários nativos ARM64 concluídos no ambiente de compilação usado nesta sessão.

As opções de Turnip e de apresentação são alternativas de diagnóstico, não uma correção comprovada da tela preta. Um driver externo pode ser incompatível com o aparelho ou com o pacote escolhido.

## Problemas observados

O logcat enviado pelo usuário veio de uma build anterior à integração final do seletor Turnip. Nele:

1. O backend Vulkan foi inicializado e uma swapchain foi criada; depois o runtime retomou a thread principal do jogo com retorno `00000000`.
2. Há erros de alocação de `GraphicBuffer` de 4×4 no formato 56 e mensagens `AdrenoUtils`/`qdgralloc` sobre metadados ou layout de memória. A relação desses erros com a tela preta ainda não foi estabelecida.
3. O jogo/runtime tentou abrir `game:\shaders\SobelEdge.xpu` e `game:\shaders\VertexColorFillPS.xpu`; o VFS respondeu que não encontrou os arquivos (`0xC000000F`). Não foi confirmado se são recursos ausentes na extração, aliases esperados ou um problema de carregamento/conversão de shaders.
4. Nesse log **não aparece** `FATAL EXCEPTION`, `SIGSEGV`, `SIGABRT` nem um tombstone que explique um crash nativo. A ausência dessas mensagens naquele arquivo não prova que o processo nunca caiu; o trecho recebido é insuficiente para identificar a causa.

O relato atual continua sendo: **fecha ou apresenta tela preta; jogabilidade e renderização do Castle Crashers não estão confirmadas**.

## O que falta

- Reproduzir a falha com os logs da build atual e identificar se é encerramento do processo, falha do driver, falha do guest ou somente ausência de desenho.
- Investigar os acessos a `SobelEdge.xpu` e `VertexColorFillPS.xpu`: confirmar se os arquivos existem no conteúdo de origem e no diretório instalado, verificar nomes/capitalização e rastrear a rotina que os solicita.
- Diagnosticar a sequência de criação de `AHardwareBuffer`, swapchain e primeiro frame no Adreno 650, comparando driver Samsung e um pacote Turnip conhecido e compatível.
- Corrigir as lacunas de compatibilidade do runtime/recompilação e do caminho de shaders para este título.
- Implementar e validar renderização nativa **específica do Castle Crashers**, caso essa seja a direção do projeto. O renderizador de cenas do Skate 3 usa dados e lógica daquele jogo e não pode ser ativado no Castle Crashers por copiar suas Cvars ou fontes.
- Testar controles/toque, áudio, saves, sessões de jogo e estabilidade em aparelhos físicos. Nenhum desses itens deve ser considerado validado apenas porque o APK compilou.
- Atualizar o script `scripts/build-android.ps1`, que antecede a estrutura `jniLibs`/AdrenoTools atual, e tornar o preparo de assets reprodutível. O build Android Studio pode compilar o app quando assets e bibliotecas necessárias já estão no lugar; isso não significa que um clone público limpo esteja pronto para executar o jogo.

## Como ajudar

Abra uma issue com:

- modelo exato do aparelho, SoC/GPU e versão Android;
- versão/hash do APK, opção escolhida (driver do sistema ou Turnip) e nome/versão do pacote de driver;
- passos para reproduzir, duração até a tela preta/fechamento e se o processo ainda aparece em `pidof`;
- logs novos da mesma execução: `bootstrap.log`, `logcat.log`, `native.log`, `app.log` e `fatal.log`, quando existirem;
- indicação de quais mensagens aparecem para Vulkan, criação da swapchain, thread principal e shaders `.xpu`.

Antes de enviar, remova dados pessoais dos logs. **Não anexe jogos, ISOs, pacotes XBLA/STFS, `default.xex`, dumps, saves, assets extraídos ou APKs que contenham esses dados.** Para comparar, use apenas cópias obtidas legalmente e compartilhe metadados não protegidos e mensagens de diagnóstico.

Contribuições úteis incluem reproduções independentes, análise do fluxo de carregamento de shaders, testes de driver com resultados documentados, diagnóstico de crash/tombstone e implementação de soluções específicas deste título. Evite copiar sem adaptação funções, Cvars, endereços de funções ou o renderizador nativo do Skate 3: esses componentes são específicos do Skate 3.

## Build Android

O build validado nesta sessão usou JDK 21, Android SDK Platform 35, Android NDK 27.2.12479018, CMake 3.25+ e Ninja. Para compilar o projeto Android com os assets e bibliotecas ARM64 já preparados:

```bash
cd android
./gradlew --no-daemon assembleDebug
```

O APK debug é criado em `android/app/build/outputs/apk/debug/app-debug.apk`. No Windows, use `gradlew.bat --no-daemon assembleDebug`. Esse comando compila e empacota; **não corrige as falhas de execução descritas acima**.

Para reconstruir as bibliotecas nativas a partir do projeto completo, o SDK ReXGlue é um submódulo definido em `.gitmodules`. É necessário inicializá-lo e fornecer uma cópia de jogo legalmente obtida e preparada localmente (`game/default.xex` e seus dados). O fluxo previsto é:

```bash
git submodule update --init --recursive
export ANDROID_NDK_ROOT="$HOME/Android/Sdk/ndk/27.2.12479018"
cmake --preset host-codegen
cmake --build --preset host-codegen --target generate-castle --parallel 4
cmake --preset android-release
cmake --build --preset android-release --target castle --parallel 4
cd android
./gradlew --no-daemon assembleDebug
```

A preparação/estágio dos dados do jogo não está automatizada para um clone limpo. Não publique uma build reproduzível antes de documentar e testar esse fluxo sem incluir conteúdo protegido.

## Segurança, direitos e licenciamento

Castle Crashers e seus materiais são propriedade de seus titulares. Este repositório não deve distribuir o jogo, seu pacote XBLA/STFS, executáveis, assets, dumps, saves ou APKs que os incorporem. Não envie esses arquivos ao GitHub; mantenha-os apenas em instalações locais autorizadas. O usuário é responsável por cumprir a legislação aplicável e os termos das ferramentas utilizadas.

O repositório também depende de componentes de terceiros, incluindo o SDK ReXGlue e AdrenoTools. **A licença do projeto completo ainda precisa ser revisada e declarada**; não presuma que o código, os binários gerados e os materiais de terceiros compartilham a mesma licença. Confira as licenças de cada dependência antes de redistribuir ou aceitar contribuições sob uma licença única.
