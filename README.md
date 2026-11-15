## Licence

Copyright (C) 2026 [j-pirate]

Ce projet est distribué sous licence
GNU General Public License version 3.0.

Voir le fichier LICENSE pour les conditions complètes.

# Langue

Application Android native d’apprentissage du vocabulaire, générique et entièrement locale. Le dépôt fournit le moteur, l’interface et le format de contenu ; **l’application universelle ne contient aucun vocabulaire ni pack pédagogique par défaut**.

Au premier lancement, ses trois onglets **Vocabulaire · Quiz · Paramètres** sont disponibles. Pour commencer, ouvrir **Paramètres → Importer un pack** et sélectionner une archive ZIP compatible. Aucun compte, serveur, abonnement ou téléchargement n’est nécessaire après l’installation de l’APK et du pack.

## Fonctionnalités

- Catégories produites à partir du pack, recherche dans les textes source/cible, variantes et translittérations ; recherche indépendante de la casse et des accents, affichage Unicode conservé.
- Fiches de vocabulaire, traductions alternatives, translittération facultative, indication de consultation et audio local facultatif.
- Quiz à trois choix issus du contenu installé, réponses mélangées, correction immédiate, série courante, record et statistiques persistants. Les équivalences connues sont regroupées ; un contenu insuffisant produit un état explicatif.
- Plusieurs packs installés, sélection de la combinaison de langues réellement disponible, mise à jour et suppression avec confirmation.
- Export/restauration JSON de la progression et réinitialisation confirmée du pack actif.
- Variantes `universal` et `dedicated` utilisant exactement le même moteur.

L’interface est en français. Les langues source et cible viennent des packs et sont indépendantes de la langue de l’interface. Le style utilise un fond beige, des accents brique et des titres à empattements ; aucune ressource d’Asslema n’est réutilisée. Aucune vidéo de référence n’a été fournie dans le dépôt : une reproduction visuelle exacte n’est pas revendiquée.

## Compiler et exécuter

Prérequis : **JDK 21**, Android SDK avec `platform-tools`, `platforms;android-35` et `build-tools;34.0.0` (utilisé par AGP) et `build-tools;35.0.0`. La sortie JVM cible Java 17 ; le projet utilise Gradle 8.9, Android Gradle Plugin 8.7.3 et Kotlin 2.0.21. Android minimum : **8.0 / API 26**, cible : **API 35**.

Android Studio peut installer le SDK et ouvrir directement ce dossier. Pour une installation en ligne de commande, installer les outils officiels Android puis :

```sh
sdkmanager --licenses
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;34.0.0' 'build-tools;35.0.0'
```

Définir `ANDROID_HOME` vers le SDK, ou créer un fichier local non versionné `local.properties` contenant `sdk.dir=/chemin/absolu/vers/android-sdk`. Ne pas ajouter les clés de signature ni la configuration locale au dépôt.

Dans cet environnement cloud, `bash scripts/cloud-setup.sh` installe un JDK 21 complet (Temurin, avec `javac` et `jlink`) et les outils Android dans `/workspace/tools`, configure les répertoires inscriptibles et le proxy Java éventuel, puis lance les vérifications. Le script vérifie les SHA-256 des téléchargements et accepte les conditions du SDK affichées par `sdkmanager`. Il ne stocke aucun secret dans Git.

Depuis la racine du dépôt :

```sh
./gradlew :engine:test :app:testUniversalDebugUnitTest :app:assembleUniversalDebug :app:lintUniversalDebug
```

APK de développement : `app/build/outputs/apk/universal/debug/app-universal-debug.apk`.

```sh
adb install -r app/build/outputs/apk/universal/debug/app-universal-debug.apk
adb shell am start -n org.langue.universal/org.langue.app.MainActivity
```

Le build télécharge les dépendances Gradle/Maven ; **l’application installée fonctionne hors ligne** et ne déclare aucune permission Internet. La sélection des fichiers passe par le sélecteur de documents Android, sans permission globale d’accès au stockage.

## Ajouter son contenu

Lire la [spécification des packs](docs/PACK_FORMAT.md), préparer les trois fichiers JSON et les éventuels enregistrements dans `audio/`, puis créer le ZIP. Les identifiants de pack, de catégorie et d’élément doivent rester stables entre les versions.

Valider avant distribution :

```sh
./gradlew :engine:run --args="/chemin/absolu/mon-pack.zip"
```

Le validateur utilise la même extraction bornée et les mêmes règles que l’import Android. Un code de sortie non nul signale un refus. Les contenus de test sont exclusivement dans les sources de test ; ne pas les copier dans les ressources de production.

Pour demander ultérieurement la création d’un pack : fournir la combinaison de langues, les catégories, les textes, les variantes et les audios autorisés, et demander un ZIP conforme au schéma 1. Aucun écran ni fichier Kotlin n’a besoin d’être modifié.

## Applications dédiées

Voir les [instructions de variantes et de publication](docs/VARIANTS.md). La variante dédiée embarque un ZIP local dans l’APK et l’importe au premier lancement. Son nom, son identifiant, son icône, son accent et ses métadonnées sont configurables. Chaque identifiant Android possède son propre stockage et sa propre progression.

## Architecture et données

Voir [l’architecture](docs/ARCHITECTURE.md) et [le format de sauvegarde](docs/PROGRESS_FORMAT.md).

| Emplacement | Responsabilité |
| --- | --- |
| `engine/` | Modèles versionnés, validation/extraction ZIP, recherche Unicode, génération de quiz, validateur CLI et tests JVM |
| `app/.../ui/` | Interface Compose, état MVVM, navigation, confirmations et sélecteurs de documents |
| `app/.../data/` | Room, DataStore, installation/mise à jour des packs, progression et sauvegardes |
| `app/.../audio/` | Lecture locale Android avec une seule lecture active et libération des ressources |
| `app/src/main/res/values/strings.xml` | Textes traduisibles de l’interface |

Les statistiques et contenus importés restent dans le stockage privé de l’application. Supprimer un pack supprime également sa progression locale ; exporter auparavant si nécessaire. Les sauvegardes de progression ne contiennent pas le vocabulaire ni les fichiers audio. La sauvegarde automatique Android est désactivée ; l’export explicite reste sous le contrôle de l’utilisateur.

## Vérifications et maintenance

Consulter le [bilan des vérifications réellement exécutées](docs/VALIDATION.md), qui distingue les tests réussis des vérifications bloquées et non exécutées.

Les commandes ci-dessus exécutent les tests disponibles et produisent les rapports sous `engine/build/reports/tests/`, `app/build/reports/tests/` et `app/build/reports/lint-results-universalDebug.html`. La CI dans `.github/workflows/android.yml` reprend ce workflow. Sa présence ne constitue pas une preuve d’exécution sur GitHub.

Le [guide de tests Android dans le cloud](docs/DEVICE_TESTS.md) décrit aussi le démarrage d’un émulateur local en mode logiciel et les rapports produits.

Pour les tests instrumentés, connecter un appareil ou un émulateur Android API 26 ou supérieure, puis :

```sh
./gradlew :app:connectedUniversalDebugAndroidTest
```

Avant une publication, vérifier également sur un appareil réel : premier démarrage vide, trois onglets, import d’un pack personnel, recherche et écriture RTL, lecture MP3/M4A, quiz, redémarrage, mise à jour du même pack et restauration d’une sauvegarde. Les tests JVM ne remplacent pas la vérification du décodage audio et des sélecteurs de documents sur Android.

Les versions des dépendances sont explicites dans les fichiers Gradle. Garder le schéma documenté compatible ou prévoir une migration et ses tests avant de changer les données persistées. Aucun secret, service tiers ou variable d’authentification n’est requis par l’application.
