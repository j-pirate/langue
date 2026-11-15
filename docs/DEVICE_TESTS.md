# Tests sur Android dans le cloud

Les tests instrumentés exécutent la véritable application et Room dans Android, sans serveur applicatif. Les données synthétiques et les enregistrements de contrôle restent dans `app/src/androidTest` ; l’APK universelle ne les contient pas.

Dans cet environnement Linux, le répertoire personnel système est en lecture seule et KVM est absent. Le chemin vérifié utilise un émulateur AOSP Android 9 / API 28 x86_64, le rendu logiciel et un client Python du protocole ADB local. Il ne modifie pas le répertoire personnel système.

Depuis `/workspace/langue` :

```sh
bash scripts/cloud-setup.sh --build-only --with-emulator
bash scripts/start-cloud-emulator.sh
/workspace/tools/adb-venv/bin/python scripts/run-device-tests.py
```

`--build-only` exécute les tests du moteur et compile les APK ainsi que les tests JVM et instrumentés ; il ne prétend pas exécuter les tests Android. Le script Python attend le démarrage réel d’Android, installe les deux APK et lance le runner AndroidJUnitRunner. Il exige sept résultats réussis et un bilan `OK`, puis capture l’écran vide.

Le premier démarrage logiciel peut prendre plusieurs minutes. Les rapports sont dans `app/build/reports/device/instrumentation.txt` et `app/build/reports/device/empty-app.png`. Le journal de l’émulateur est dans `/workspace/tools/emulator.log`. Les processus ne sont pas conservés par une publication de l’environnement : relancer le script de démarrage dans une nouvelle tâche. Une tâche cloud possède déjà son checkout isolé ; aucun worktree supplémentaire n’est nécessaire.

Ce client ADB est réservé à l’émulateur de test local non authentifié, sur `127.0.0.1:5555`. Pour un appareil physique ou un environnement Android Studio standard, utiliser plutôt :

```sh
./gradlew :app:connectedUniversalDebugAndroidTest
```

Les tests vérifient le lancement vide et les trois onglets, un parcours de contenu importé, la recherche, les détails, le quiz et la réinitialisation confirmée ; trois tests du dépôt couvrent aussi les mises à jour, la persistance, les sauvegardes et les refus d’import. Deux tests audio contrôlent le décodage d’un WAV local et la gestion d’un fichier absent. Vérifier les formats MP3/M4A, les sorties audio physiques et le sélecteur de documents sur les appareils de diffusion reste utile avant publication.

La suite JVM Robolectric et lint se lancent avec :

```sh
source scripts/activate-cloud.sh
./gradlew :app:testUniversalDebugUnitTest :app:lintUniversalDebug
```

Maven redirige une dépendance native Robolectric vers `gcp.mirrors.maven.org`. Dans un environnement réseau restreint, ce domaine doit être autorisé en plus des registres Maven habituels. L’application installée ne contacte aucun de ces registres et ne demande aucune permission Internet.
