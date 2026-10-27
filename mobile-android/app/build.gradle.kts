plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// O Firebase so entra quando a configuracao do projeto existe. Sem ela o app
// compila e funciona igual — e o painel inteiro dentro do WebView —, so nao
// oferece "Receber neste aparelho". Exigir o arquivo para compilar obrigaria
// quem clona o repositorio publico a criar um projeto no Firebase antes de ver
// a primeira tela.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

base {
    archivesName.set("flightmonitor")
}

android {
    namespace = "com.flightmonitor.app"
    compileSdk = 34

    defaultConfig {
        // Precisa ser o MESMO pacote cadastrado no app Android do Firebase: o
        // google-services.json e emitido para ele, e com outro o build falha.
        applicationId = "com.flightmonitor.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // O endereco do painel no prod. Trocavel sem mexer em codigo:
        //   ./gradlew assembleDebug -PpainelUrl=https://outro.endereco
        val painel = (project.findProperty("painelUrl") as String?)
            ?: "https://prod.tail8feb71.ts.net:10000"
        buildConfigField("String", "PAINEL_URL", "\"$painel\"")
    }

    signingConfigs {
        create("release") {
            // Caminho e senha vem do ambiente — nada de segredo versionado. Sem
            // as variaveis, o build de release falha de forma explicita em vez
            // de cair silenciosamente na chave de debug.
            storeFile = file(System.getenv("FLIGHTMONITOR_KEYSTORE") ?: "keystore-ausente.jks")
            storePassword = System.getenv("FLIGHTMONITOR_KEYSTORE_PASSWORD")
            keyAlias = "flightmonitor"
            keyPassword = System.getenv("FLIGHTMONITOR_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 33.x, e nao a mais nova: e a ultima linha que compila com o SDK 34 e o
    // AGP 8.2 que o tour-guide ja usa nesta maquina. Subir exigiria trocar o
    // toolchain inteiro, sem nada que o push precise.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
}
