# Développement et maintenance

Pour le téléchargement et la publication d’une APK, voir [GITHUB_RELEASES.md](GITHUB_RELEASES.md). Les identités universelle et dédiée, les autres livrables et la signature sont détaillés dans [VARIANTS.md](VARIANTS.md).

## Prérequis

- JDK **21** complet, avec `javac` et `jlink` ; la sortie JVM cible Java 17.
- Android SDK : `platform-tools`, `platforms;android-35`, `build-tools;34.0.0` (utilisé par AGP) et `build-tools;35.0.0`.
- Versions du projet : Gradle **8.9**, Android Gradle Plugin **8.7.3**, Kotlin **2.0.21**.
- Android minimum : **8.0 / API 26** ; cible : **API 35**.

Android Studio peut installer le SDK et ouvrir directement la racine du dépôt. Avec les outils officiels Android en ligne de commande :

```sh
sdkmanager --licenses
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;34.0.0' 'build-tools;35.0.0'
```

Définir `ANDROID_HOME` vers le SDK, ou créer un fichier local non versionné `local.properties` contenant `sdk.dir=/chemin/absolu/vers/android-sdk`. Ne pas ajouter de configuration locale, de clé de signature ou de secret au dépôt.

## Environnement cloud

```sh
bash scripts/cloud-setup.sh
```

Ce script installe Temurin JDK 21 et les outils Android dans `/workspace/tools`, configure les répertoires inscriptibles, le proxy Java éventuel et la confiance TLS système, puis lance les vérifications. Il vérifie les SHA-256 des téléchargements et accepte les conditions du SDK affichées par `sdkmanager`. Il ne stocke aucun secret dans Git.

Dans un nouveau shell, retrouver les chemins de cet environnement avec :

```sh
source scripts/activate-cloud.sh
```

Pour les options d’installation d’un émulateur, voir [DEVICE_TESTS.md](DEVICE_TESTS.md).

## Compiler, tester et lancer

Depuis la racine du dépôt :

```sh
./gradlew :engine:test :app:testUniversalDebugUnitTest :app:assembleUniversalDebug :app:lintUniversalDebug
```

APK de développement : `app/build/outputs/apk/universal/debug/app-universal-debug.apk`. Cette APK Debug est signée avec une clé de développement ; elle ne constitue pas une version de production.

Pour l’installer et l’ouvrir sur un appareil ou un émulateur connecté :

```sh
adb install -r app/build/outputs/apk/universal/debug/app-universal-debug.apk
adb shell am start -n org.langue.universal/org.langue.app.MainActivity
```

La compilation télécharge les dépendances Gradle/Maven. L’application installée fonctionne hors ligne et ne déclare aucune permission Internet. La sélection des fichiers utilise le sélecteur de documents Android, sans permission globale d’accès au stockage.

## Tests, rapports et intégration continue

Les commandes précédentes produisent les rapports sous :

- `engine/build/reports/tests/` ;
- `app/build/reports/tests/` ;
- `app/build/reports/lint-results-universalDebug.html`.

Le workflow `.github/workflows/android.yml` exécute les contrôles configurés et conserve leurs livrables. Sa présence ne prouve pas qu’une exécution GitHub a réussi : consulter les résultats du lancement concerné et le [bilan des vérifications](VALIDATION.md).

Le [guide Android](DEVICE_TESTS.md) contient les commandes des tests instrumentés, les émulateurs logiciels et les rapports associés. Avant une publication, effectuer également le parcours sur appareil décrit dans ce guide. Les tests JVM ne remplacent pas le décodage audio ni les sélecteurs de documents réels.

## Contenu et maintenance

- Pour créer ou valider un ZIP de contenu, suivre [PACK_FORMAT.md](PACK_FORMAT.md). Aucun écran ni fichier Kotlin ne doit être modifié pour ajouter une langue.
- Pour une application avec un pack embarqué, suivre [VARIANTS.md](VARIANTS.md). Chaque identifiant Android possède son propre stockage et sa propre progression.
- Les responsabilités des modules et les diagnostics sécurisés sont décrits dans [ARCHITECTURE.md](ARCHITECTURE.md) ; les sauvegardes et leur conservation dans [PROGRESS_FORMAT.md](PROGRESS_FORMAT.md).
- Les versions des dépendances sont explicites dans les fichiers Gradle. Maintenir les schémas documentés compatibles ou prévoir une migration et ses tests avant de modifier les données persistées.
- Les jeux de données de test restent dans les sources de test, jamais dans les ressources de production.
- Aucun secret, service tiers ni variable d’authentification n’est requis par l’application.

## Dépannage

| Situation | Vérification |
| --- | --- |
| SDK introuvable | Vérifier `ANDROID_HOME` ou le chemin absolu `sdk.dir` dans le fichier local ignoré `local.properties`. |
| `javac` ou `jlink` absent | Utiliser un JDK 21 complet et vérifier le shell activé dans le cloud. |
| Dépendances impossibles à télécharger | Vérifier l’accès aux registres Maven et la configuration du proxy/TLS ; les particularités Robolectric sont dans [DEVICE_TESTS.md](DEVICE_TESTS.md). |
| Installation refusée après une autre APK | Contrôler l’identifiant et le certificat de signature ; suivre [GITHUB_RELEASES.md](GITHUB_RELEASES.md) avant toute désinstallation pour préserver les données. |
| Écran vide au premier démarrage | C’est l’état normal de l’application universelle : importer son ZIP depuis **Paramètres → Importer un pack**. |
| Pack refusé ou quiz indisponible | Utiliser le validateur du [format des packs](PACK_FORMAT.md), contrôler la version et les références, puis les trois groupes de réponses distincts requis par le quiz. |
| Audio indisponible | Vérifier les chemins locaux du pack et les codecs Android de l’appareil ; une extension autorisée ne garantit pas le décodage. |

Les messages d’import, de version, d’audio, de catégorie, de sauvegarde et de stockage sont distingués en français. Les journaux applicatifs indiquent les opérations, codes et classes d’erreur, sans contenu des packs ni chemins privés. Consigner une nouvelle vérification avec son résultat réel et ses limites, sans présenter une commande disponible comme un test exécuté.

## Préparer le paquet de téléchargement de test

La première publication préparée utilise la version Android **1.1.0**, code **2**, et le tag GitHub **v1.1.0-test.1**. Depuis la racine du projet, avec les outils Android activés :

```sh
./gradlew :engine:test :app:testUniversalDebugUnitTest :app:lintUniversalDebug :app:assembleUniversalDebug \
  -PappVersionCode=2 -PappVersionName=1.1.0
./scripts/package-test-apk.sh
```

Le dossier `app/build/distribution/test/` contient `langue.apk`, `RELEASE_NOTES.md` et `SHA256SUMS.txt`. Le script vérifie la signature existante avec `apksigner`, le certificat Debug, le caractère débogable de l’APK, son identifiant universel et sa version. Il refuse une APK non signée ou malformée, une version différente et des notes ne mentionnant pas le statut de test. Il ne crée aucune clé et ne publie rien.

Pour vérifier les deux fichiers après téléchargement :

```sh
cd app/build/distribution/test
sha256sum -c SHA256SUMS.txt
```

Le workflow GitHub Actions conserve l’artefact historique `universal-debug-apk` et fournit aussi `langue-installation-test`, limité aux trois fichiers ci-dessus et conservé 90 jours. Ces artefacts temporaires ne sont pas des Releases. Les étapes de publication manuelle et d’activation du lien permanent sont dans [GITHUB_RELEASES.md](GITHUB_RELEASES.md).

Pour une version ultérieure, mettre à jour ensemble les paramètres de compilation, les versions attendues par le script de paquetage, `RELEASE_NOTES.md` et le numéro proposé dans le guide de publication. Une version de production utilise la signature durable du propriétaire, jamais ce paquet Debug.
