# Tests sur Android dans le cloud

Les tests instrumentés exécutent la véritable application et Room dans Android, sans serveur applicatif. Les données synthétiques et les enregistrements de contrôle restent dans `app/src/androidTest` ; l’APK universelle ne les contient pas.

Dans cet environnement Linux, le répertoire personnel système est en lecture seule et KVM est absent. Le chemin vérifié utilise un émulateur AOSP x86_64, sélectionnable parmi Android 8.0 / API 26, Android 9 / API 28 et Android 15 / API 35, le rendu logiciel et un client Python du protocole ADB local. Il ne modifie pas le répertoire personnel système.

Depuis `/workspace/langue` :

```sh
bash scripts/cloud-setup.sh --build-only --with-emulator
bash scripts/start-cloud-emulator.sh
/workspace/tools/adb-venv/bin/python scripts/run-device-tests.py
```

`--build-only` exécute les tests du moteur et compile les APK ainsi que les tests JVM et instrumentés ; il ne prétend pas exécuter les tests Android. Le script Python attend le démarrage réel d’Android, installe les deux APK et lance le runner AndroidJUnitRunner pour le paquet `org.langue.app`, avec un nombre attendu de douze tests. Il exige douze résultats réussis, le bilan `OK` et le code final AndroidJUnitRunner `-1`. Son code de sortie reflète ces résultats. La capture d’écran de fin est une opération distincte, demandée avec `--capture-screen`.

Pour demander également une capture d’écran après la suite :

```sh
/workspace/tools/adb-venv/bin/python scripts/run-device-tests.py --capture-screen
```

Ce mode vérifie la présence de l’écran vide dans une hiérarchie Android fraîche avant de capturer l’image. S’il échoue, il signale séparément l’échec de cette étape après le bilan des tests. Sans ce drapeau, aucun second lancement à froid n’est imposé après les douze tests.

Le premier démarrage logiciel peut prendre plusieurs minutes. Les rapports sont dans `app/build/reports/device/api26/`, `api28/` ou `api35/` : `instrumentation.txt` et `empty-app.png`. Le journal de l’émulateur est dans `/workspace/tools/emulator.log`. Les processus ne sont pas conservés par une publication de l’environnement : relancer le script de démarrage dans une nouvelle tâche. Une tâche cloud possède déjà son checkout isolé ; aucun worktree supplémentaire n’est nécessaire.

Ce client ADB est réservé à l’émulateur de test local non authentifié, sur `127.0.0.1:5555`. Pour un appareil physique ou un environnement Android Studio standard, utiliser plutôt :

```sh
./gradlew :app:connectedUniversalDebugAndroidTest
```

Pour sélectionner Android 8.0 ou Android 15, préfixer chacune des trois commandes ci-dessus par `LANGUE_TEST_API=26` ou `LANGUE_TEST_API=35`. L’installation télécharge alors l’image demandée et le lanceur crée un AVD distinct. Arrêter l’émulateur de test précédent avant de changer de version ; le PID de celui lancé par le script figure dans `/workspace/tools/langue-emulator.pid`. Le runner refuse une API différente de celle demandée et ne compte pas un rapport d’une exécution antérieure.

Les tests vérifient le lancement vide et les trois onglets, un parcours de contenu importé, la recherche, les détails, le quiz et la réinitialisation confirmée ; trois tests du dépôt couvrent aussi les mises à jour, la persistance, les sauvegardes et les refus d’import. Les tests audio contrôlent réellement les décodeurs WAV, MP3 et M4A, ainsi qu’un fichier absent ou corrompu. Les tests Unicode vérifient aussi la direction RTL et les sauvegardes après réouverture. Contrôler les sorties audio physiques et le sélecteur de documents sur les appareils de diffusion reste utile avant publication.

La suite JVM Robolectric et lint se lancent avec :

```sh
source scripts/activate-cloud.sh
./gradlew :app:testUniversalDebugUnitTest :app:lintUniversalDebug
```

Le répertoire personnel du processus JVM de test est isolé dans le cache Gradle, ce qui permet de créer le verrou de téléchargement Robolectric sur une machine au répertoire personnel système non inscriptible. Les tests de présentation d’erreurs couvrent les SDK 26 et 34.

Maven redirige une dépendance native Robolectric vers `gcp.mirrors.maven.org`. Dans un environnement réseau restreint, ce domaine doit être autorisé en plus des registres Maven habituels. L’application installée ne contacte aucun de ces registres et ne demande aucune permission Internet.
