# Validation du 15 novembre 2026

Vérifications réalisées dans l’environnement cloud avec un JDK Temurin 21 complet, Gradle 8.9 et le SDK Android 35.

| Vérification | Résultat observé |
| --- | --- |
| `:engine:test` | 28 tests exécutés, 28 réussis, aucun ignoré |
| APK universelle Debug | Compilée ; signature de développement vérifiée ; aucun `assets/packs/` |
| APK dédiée Debug | Compilée avec `org.langue.buildtest`, le nom `L'atelier & langues` et le ZIP de contrôle exact |
| Sources de tests JVM Android | Compilation réussie |
| APK des tests instrumentés | Compilation réussie |
| Tests instrumentés sur Android 9 / API 28 AOSP x86_64 | 7 tests exécutés, 7 réussis, bilan AndroidJUnitRunner `OK (7 tests)` |
| Interface vide | Écran rendu inspecté après les tests ; trois onglets utilisables ; démarrage revérifié après redémarrage de l’émulateur |
| Suite Robolectric | Non exécutée : dépendance native bloquée par une redirection Maven vers `gcp.mirrors.maven.org` |
| Lint | Non terminé : la génération du modèle des tests rencontre la même dépendance bloquée |

Les sept tests Android couvrent le premier lancement et les onglets, un pack importé avec catégories/détails/variantes/translittération/recherche, une réponse de quiz et ses statistiques, les confirmations de réinitialisation, la conservation de progression après réouverture et mise à jour, les sauvegardes, les refus d’import/restauration, la résolution locale de l’audio, le décodage et la lecture réelle d’un WAV, et la gestion d’un fichier absent.

Les fixtures sont limitées aux sources de test. La variante dédiée de contrôle n’est pas une application linguistique à distribuer ; son pack est vide de vocabulaire et sert à vérifier l’intégration d’un ZIP externe. L’APK universelle est la livraison vierge destinée au développement et aux essais.

Rapports locaux générés :

- `engine/build/reports/tests/test/index.html` et `engine/build/test-results/test/`.
- `app/build/reports/device/instrumentation.txt` et `empty-app.png`.

Pour reproduire les vérifications Android, voir [DEVICE_TESTS.md](DEVICE_TESTS.md). Les sorties de compilation et rapports sont ignorés par Git ; ils sont régénérés par les commandes documentées.

L’autorisation de `gcp.mirrors.maven.org` a été enregistrée dans le brouillon de configuration de l’environnement. Elle reste à enregistrer dans les paramètres de la machine pour terminer Robolectric et lint. La publication de l’environnement et une restauration dans une nouvelle tâche n’ont pas été effectuées par l’agent. La CI GitHub a été ajoutée mais n’a pas été exécutée sur GitHub pendant cette intervention.

Le sélecteur de documents Android utilise les API SAF réelles, mais son interaction avec les différents fournisseurs de fichiers et la lecture de MP3/M4A sur les appareils de diffusion ne sont pas validées par ces tests WAV. L’APK est une version Debug ; une distribution publique demande une signature de publication sous le contrôle de son propriétaire.
