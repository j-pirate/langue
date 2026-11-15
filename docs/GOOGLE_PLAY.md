# Préparer une publication Google Play

Ce guide décrit la préparation et le dépôt manuel d’une version par son propriétaire. Il ne publie rien, ne crée pas de compte Google Play et ne fournit pas de clé de signature. Les résultats réellement vérifiés dans cet environnement sont consignés dans [VALIDATION.md](VALIDATION.md).

## Identité et exigences à vérifier

Choisir l’application à distribuer : `universal` sans contenu initial, ou `dedicated` avec son pack embarqué. Les propriétés de nom, d’identifiant, d’icône et de pack sont décrites dans [VARIANTS.md](VARIANTS.md). L’identifiant Android est l’identité de l’application dans Google Play : le conserver pour ses mises à jour et en choisir un distinct pour chaque application autonome.

Le projet cible actuellement **Android API 35** (`targetSdk = 35`). Cela ne prouve pas sa conformité aux exigences Google Play au moment du dépôt. Vérifier la [politique officielle de niveau d’API cible](https://support.google.com/googleplay/android-developer/answer/11926878) avant chaque publication. Si le niveau exigé est supérieur, mettre à jour `targetSdk`, les prérequis de compilation nécessaires et les comportements Android concernés, puis refaire les tests avant l’envoi. Une compilation réussie ne remplace pas cette vérification.

Vérifier également les exigences applicables au compte développeur, à sa vérification et aux parcours de test imposés par Play Console. Elles peuvent différer selon le type et l’ancienneté du compte.

## Version Android et version de pack

Passer les numéros Android à Gradle sans modifier le moteur :

```sh
./gradlew :app:bundleUniversalRelease \
  -PappVersionCode=2 \
  -PappVersionName=1.0.1
```

`appVersionCode` est un entier strictement positif, `1` par défaut. Pour une mise à jour, utiliser une valeur supérieure à celles déjà envoyées pour le même identifiant, y compris sur les pistes de test. `appVersionName` est le nom de version visible, `1.0.0` par défaut ; il ne doit pas être vide. Les mêmes propriétés s’appliquent aux deux variantes.

La version entière du manifeste d’un pack est distincte : augmenter sa `version` pour mettre à jour le contenu pédagogique avec le même identifiant de pack. Publier un nouvel AAB nécessite toujours un nouveau `appVersionCode`, même si seule la version du pack embarqué a changé.

## Signature sous le contrôle du propriétaire

Les APK `Debug` utilisent une clé de développement et ne sont pas des livrables de publication. Une sortie `Release` n’est signée avec votre identité que si une configuration de signature valide a été fournie.

Le build prend en charge quatre variables d’environnement :

| Variable | Rôle |
| --- | --- |
| `LANGUE_KEYSTORE_FILE` | Chemin absolu vers le keystore de téléversement existant du propriétaire |
| `LANGUE_KEYSTORE_PASSWORD` | Mot de passe de ce keystore |
| `LANGUE_KEY_ALIAS` | Alias de la clé de téléversement |
| `LANGUE_KEY_PASSWORD` | Mot de passe de cette clé |

Fournir **les quatre ensemble**, ou aucune. Une configuration partielle ou vide est refusée. Sans ces variables, les commandes Release peuvent compiler des sorties non signées ; elles ne constituent pas des fichiers prêts à envoyer sur Google Play.

Utiliser la clé de téléversement existante correspondant à l’application. Les variables peuvent être injectées par le gestionnaire de secrets de votre environnement de compilation, sans mettre les valeurs dans les commandes ni le dépôt. Sur un poste de confiance, une saisie manuelle Bash évite d’inscrire les valeurs dans l’historique :

```sh
read -r -p 'Chemin absolu du keystore existant : ' LANGUE_KEYSTORE_FILE
read -r -p 'Alias de la clé : ' LANGUE_KEY_ALIAS
read -r -s -p 'Mot de passe du keystore : ' LANGUE_KEYSTORE_PASSWORD
printf '\n'
read -r -s -p 'Mot de passe de la clé : ' LANGUE_KEY_PASSWORD
printf '\n'
export LANGUE_KEYSTORE_FILE LANGUE_KEYSTORE_PASSWORD LANGUE_KEY_ALIAS LANGUE_KEY_PASSWORD
```

Exécuter ensuite la commande de compilation souhaitée dans ce même shell. Désactiver toute trace des commandes et ne jamais afficher les variables, leur environnement complet ou les fichiers de credentials. Garder le keystore hors du dépôt, même si certaines extensions sont ignorées par Git. Ne pas copier les valeurs dans `gradle.properties`, des scripts, des rapports ou le chat.

Après compilation, retirer ces variables du shell lorsque leur utilisation est terminée :

```sh
unset LANGUE_KEYSTORE_FILE LANGUE_KEYSTORE_PASSWORD LANGUE_KEY_ALIAS LANGUE_KEY_PASSWORD
```

Aucune clé n’est créée par ce projet. Si le propriétaire ne dispose pas encore de sa clé ou doit réinitialiser une clé de téléversement, effectuer cette démarche séparément sous son contrôle avant de préparer le livrable signé.

Avec **Play App Signing**, la clé de téléversement signe le bundle envoyé ; Google Play utilise la clé de signature de l’application pour signer les APK distribuées. Conserver les sauvegardes du propriétaire et vérifier que les certificats correspondent à ceux attendus dans Play Console. Voir la [documentation officielle de signature Android et Play App Signing](https://developer.android.com/studio/publish/app-signing).

## Construire les livrables

Depuis la racine du dépôt, après les tests et la configuration de signature ci-dessus :

```sh
./gradlew :engine:test :app:testUniversalDebugUnitTest :app:lintUniversalDebug
./gradlew :app:bundleUniversalRelease \
  -PappVersionCode=2 -PappVersionName=1.0.1
```

AAB universel : `app/build/outputs/bundle/universalRelease/app-universal-release.aab`.

Pour une application dédiée, fournir le contenu réel du propriétaire et l’identité définitive :

```sh
./gradlew :app:bundleDedicatedRelease \
  -PappVersionCode=2 -PappVersionName=1.0.1 \
  -PdedicatedPack=/chemin/absolu/mon-pack.zip \
  -PdedicatedId=org.monorganisation.moncarnet \
  -PdedicatedName="Mon carnet" \
  -PdedicatedSubtitle="Apprendre à mon rythme" \
  -PdedicatedAbout="Une application éditée par mon organisation." \
  -PdedicatedAccent="#A44836" \
  -PdedicatedBrandingDir=/chemin/absolu/identite/res
```

AAB dédié : `app/build/outputs/bundle/dedicatedRelease/app-dedicated-release.aab`. Le ZIP est validé avant d’être embarqué. L’option `dedicatedBrandingDir` est facultative ; lorsqu’elle est fournie, ce dossier de ressources peut remplacer `drawable/ic_launcher.xml` par une icône originale.

Un AAB ne s’installe pas directement avec `adb install`. Pour un essai local par APK, utiliser `:app:assembleUniversalRelease` ou `:app:assembleDedicatedRelease` avec les mêmes propriétés et la même signature. Les chemins APK sont récapitulés dans [VARIANTS.md](VARIANTS.md). Pour vérifier la distribution effective, installer aussi la version fournie par une piste de test Play, puis refaire le parcours hors ligne.

Vérifier le numéro de version, l’identifiant, la présence de la signature attendue et le pack embarqué sur les fichiers de cette compilation. `jarsigner -verify <fichier.aab>` permet de contrôler une signature de bundle ; un résultat indiquant une archive non signée n’est pas une validation. Pour une APK, utiliser `apksigner verify --verbose <fichier.apk>` avec les outils du SDK. La validation des signatures et la compilation ne constituent pas une acceptation par Google Play.

## Dépôt manuel dans Play Console

1. Ouvrir le compte développeur du propriétaire et créer ou sélectionner l’application correspondant à l’identifiant définitif. Vérifier l’état de Play App Signing et le certificat de téléversement.
2. Renseigner la fiche : nom, descriptions, catégorie, coordonnées de contact réelles, icône et captures de l’application finale. Utiliser des contenus dont vous détenez les droits. Pour la variante universelle, montrer son état vide réel ; pour une variante dédiée, montrer le contenu réellement embarqué.
3. Remplir les déclarations demandées dans les sections de contenu de l’application : présence de publicités, accès à l’application, public cible, classification et sécurité des données. L’application actuelle ne demande pas de compte ni d’authentification et n’intègre pas de publicité ; réévaluer ces réponses si vous ajoutez des fonctionnalités ou des SDK.
4. Fournir une politique de confidentialité exacte, hébergée sur une adresse publique réelle sous la responsabilité de l’éditeur, selon les exigences de Play Console. Aucun lien fictif n’est fourni par le projet. Expliquer le stockage local des packs/statistiques, l’import et l’export volontaires de fichiers, leur suppression et les coordonnées de contact de l’éditeur.
5. Pour la section **Sécurité des données**, examiner le code et les dépendances de la variante distribuée. L’application actuelle fonctionne localement et ne déclare pas de permission Internet ; elle stocke la progression et les packs sur l’appareil. Un export choisi par l’utilisateur va vers le fournisseur de documents qu’il sélectionne, qui peut être un service cloud distinct. Décrire fidèlement les traitements applicables ; ne pas déduire automatiquement toutes les réponses du seul état hors ligne. Voir les [instructions officielles du formulaire Sécurité des données](https://support.google.com/googleplay/android-developer/answer/10787469).
6. Créer une version sur une piste de test appropriée, téléverser l’AAB signé, rédiger les notes de version et traiter les erreurs, avertissements et exigences de test remontés par Play Console. Examiner le rapport de pré-lancement et vérifier l’installation et les mises à jour.
7. Après les tests et les déclarations nécessaires, le propriétaire décide de soumettre la version en production et de son déploiement. Le dépôt et ses commandes n’effectuent aucune de ces actions automatiquement.

La création des fiches, la saisie des coordonnées et des déclarations, l’hébergement d’une politique réelle, la signature avec la clé du propriétaire et la soumission Play restent des opérations du propriétaire. Un AAB compilé sans cette démarche n’est pas une application publiée.
