# Audit Android : validations du 15 novembre et contrôle du 20 novembre 2026

L’audit conserve Kotlin, Compose, MVVM, Room, DataStore et le schéma de packs 1. Aucun contenu pédagogique n’est ajouté à l’APK universelle. Les essais ci-dessous sont réellement exécutés, sans compter deux fois les répétitions ni les contrôles GitHub Actions.

| Vérification finale locale | Résultat |
| --- | --- |
| Tests moteur Kotlin | **43 exécutés, 43 réussis**, aucun ignoré |
| Tests unitaires Android/Robolectric | **24 exécutés, 24 réussis**, aucun ignoré ; couverture SDK 26 et 34 |
| Android Lint | **0 erreur, 7 avertissements** |
| APK universelle Debug | Compilation et signature Debug vérifiées ; aucun pack intégré |
| APK dédiée Debug | Compilation vérifiée ; nom, icône, couleur, identifiant et version personnalisés |
| AAB universel et dédié Release | Compilation et structure ZIP vérifiées ; sorties non signées, aucun secret de production utilisé |
| Android 8.0 / API 26 AOSP x86_64 | **12 tests instrumentés exécutés, 12 réussis**, bilan `OK (12 tests)` |
| Android 15 / API 35 AOSP x86_64 | **12 tests instrumentés exécutés, 12 réussis**, bilan `OK (12 tests)` |

Total : **91 exécutions de tests réussies**. Les rapports se trouvent dans `engine/build/test-results/test/`, `app/build/test-results/testUniversalDebugUnitTest/` et `app/build/reports/device/api26/` ou `api35/`.

## Ce qui est couvert

- Premier lancement vierge et navigation des trois onglets.
- Import ZIP, catégories, traductions, variantes, translittération et recherche.
- Quiz à trois réponses, correction, absence de répétition immédiate, équivalences et statistiques.
- Mise à jour de pack, conservation des identifiants stables, réouverture du stockage, export/restauration et refus atomique des fichiers invalides.
- Unicode, recherche sans accents, conservation des caractères et direction RTL du texte.
- Décodeurs Android réels pour WAV, MP3 et M4A, fichier manquant et audio corrompu. Les enregistrements sont des silences générés localement dans les seuls assets de test.
- Messages français différenciés, erreurs ENOSPC réelles, diagnostics sans messages d’exception, chemins ni contenu privé.
- Invalidation du quiz après changement/mise à jour du pack et après erreur de lecture ; reprise des lecteurs Room/DataStore après Réessayer.

Le test de charge prépare **50 000 entrées**, puis génère **20 000 questions** à partir de la même préparation. Une exécution locale a mesuré 299 ms de préparation et 69 ms pour les tirages ; ces valeurs dépendent de la machine. Un contrôle structurel vérifie aussi que les tirages ne reparcourent pas les entrées originales, avec une marge de temps de test de 30 s.

## Problèmes détectés et corrections

1. `setup-android@v3` demandait par défaut le paquet obsolète `tools`. Le workflow utilise explicitement `packages: platform-tools`, puis installe les plateformes/build-tools requis.
2. Robolectric échouait avant les tests car son verrou de téléchargement visait le répertoire personnel système en lecture seule. Le processus JVM de test utilise maintenant un répertoire personnel dédié dans le cache Gradle. Sur le code initial, les 28 tests moteur et 14 tests Android existants ont ensuite réussi.
3. Lint détectait `windowLightNavigationBar` dans les ressources API 26 alors que cet attribut demande API 27. Il est isolé dans `values-v27` ; le thème commun est conservé.
4. Le quiz reconstruisait ses équivalences à chaque question. Une préparation immuable par pack est calculée sur `Dispatchers.Default` puis réutilisée ; un tirage coûte un nombre constant d’opérations.
5. Les causes d’erreur étaient masquées ; les erreurs de lecture asynchrones pouvaient aussi échapper aux gestionnaires d’actions. Des codes typés, ressources françaises, journaux limités et lecteurs relançables corrigent ces cas.

La suite Android 15 a effectivement réussi ses douze tests, mais le script ancien a ensuite renvoyé le code 1 à cause d’une capture d’écran postérieure aux tests : l’écran attendu n’a pas été retrouvé après un nouveau démarrage à froid. Ce n’était pas un échec de ces douze cas. Le script distingue désormais les opérations : par défaut il exige les douze résultats et le code final `-1` du runner ; `--capture-screen` demande en plus la capture et conserve un échec explicite si celle-ci n’aboutit pas. La capture post-suite sur API 35 reste non validée dans cette émulation logicielle.

Le premier essai Android 15 en émulation logicielle s’est interrompu avant le premier test par un ANR de démarrage. La trace plaçait le thread principal dans la vérification DEX et le système connaissait aussi des ANR au premier démarrage. Après diagnostic et initialisation de l’appareil, le runner a été limité au paquet `org.langue.app`, en exigeant toujours les douze résultats. La réexécution a réussi les douze tests en 161,657 s. Aucun test ni assertion n’a été retiré. L’essai Android 8.0 final a réussi les douze tests en 65,155 s.

## Compilation et variantes

JDK Temurin 21 complet, Gradle 8.9, Android Gradle Plugin 8.7.3, SDK de compilation/cible 35 et minimum 26. Commande complète exécutée :

```sh
source scripts/activate-cloud.sh
./gradlew :engine:test :app:testUniversalDebugUnitTest :app:lintUniversalDebug \
  :app:assembleUniversalDebug :app:assembleUniversalDebugAndroidTest \
  :app:assembleDedicatedDebug :app:bundleUniversalRelease :app:bundleDedicatedRelease \
  -PdedicatedPack="$PWD/app/src/test/resources/variant-pack.zip" \
  -PdedicatedBrandingDir="$PWD/app/src/test/resources/branding" \
  -PdedicatedId=org.langue.audit "-PdedicatedName=L'atelier & langues" \
  -PdedicatedAccent="#346E85" -PappVersionCode=2 -PappVersionName=1.1.0
```

Le manifeste dédié donne effectivement `org.langue.audit`, le nom comportant apostrophe et esperluette, le code 2 et la version 1.1.0. L’icône compilée contient l’accent bleu du dossier de test externe. L’APK/AAB universels contiennent zéro pack ; leurs équivalents dédiés incorporent exactement le ZIP de contrôle, vide de vocabulaire.

## Vérification GitHub

Le run initial fourni dans la demande échouait avant Gradle. Après la correction du SDK, le run intermédiaire a révélé le problème de thème API 27. La compilation complète corrigée est confirmée sur le commit fonctionnel `1e7e90a4e7bf7e5ac01281f03e63cc1a49ad47a2` :

- [Workflow réussi](https://github.com/j-pirate/langue/actions/runs/43764882601), durée 5 min 25 s.
- Toutes les étapes réussies : SDK, tests moteur, tests Android unitaires, lint, APK universelle, APK dédiée et bundles Release, téléversements.
- [Artefact `universal-debug-apk`](https://github.com/j-pirate/langue/actions/runs/43764882601/artifacts/14181284123), 16,3 Mo ; connexion GitHub nécessaire pour le télécharger.
- Artefact `reports`, 51,3 Ko. Les métadonnées et conclusions des étapes ont été vérifiées dans les pages publiques du run.

Le workflow conserve les rapports même en cas d’échec et refuse un téléversement d’APK sans fichier. Il ne signe ni ne publie une application de production. Les avertissements GitHub sur les versions d’actions Node 20 et la migration d’Ubuntu n’ont pas empêché cette compilation.

Une nouvelle vérification publique le **20 novembre 2026** confirme aussi le commit publié `22638c7077bdb809510ba28f0e73a7bc9ae4b430` : [run 43767141191 réussi](https://github.com/j-pirate/langue/actions/runs/43767141191), durée 7 min 12 s, toutes les étapes réussies. [Son artefact APK](https://github.com/j-pirate/langue/actions/runs/43767141191/artifacts/14182708212) pèse 16,3 Mo ; l’artefact `reports` pèse 51,3 Ko.

## Limites explicites

- Les sorties Release sont non signées : la signature réelle exige la clé d’importation du propriétaire, conservée hors Git. Aucune clé de production n’est générée et aucune publication Google Play n’est effectuée. Voir [GOOGLE_PLAY.md](GOOGLE_PLAY.md), notamment la vérification de l’exigence actuelle de SDK cible.
- Les sorties audio physiques et les différents fournisseurs du sélecteur de documents SAF n’ont pas été testés sur des téléphones physiques ; les tests utilisent les vraies API et les décodeurs de deux versions Android émulées.
- L’API `api.github.com` reste refusée par le réseau de la machine (`CONNECT 403`). Le push Git fonctionne et les résultats Actions sont vérifiables publiquement ; l’ouverture automatique d’une nouvelle PR par `gh pr create` est donc bloquée. La PR n° 1 du seul correctif CI a été fusionnée séparément par le propriétaire ; elle ne contient pas les corrections restantes. [Comparaison de la branche de correction](https://github.com/j-pirate/langue/compare/main...fix/android-reliability).
