# Mettre l’APK en téléchargement sur GitHub

Ce guide est destiné au propriétaire du dépôt, sans commande à exécuter. La première version préparée est **Langue 1.1.0 — version de test**. Ce n’est pas une version de production.

Aucune Release n’existe au moment de la préparation. La page d’accueil annonce donc un téléchargement en attente, et non un bouton prétendument disponible.

## 1. Mettre la nouvelle page d’accueil en ligne

Les modifications sont préparées sur la branche `docs/easy-apk-download`.

1. Ouvrez [la comparaison des modifications](https://github.com/j-pirate/langue/compare/main...docs/easy-apk-download?expand=1).
2. Cliquez sur **Create pull request**. Si GitHub vous le demande, utilisez le titre **Simplifier la page d’accueil et préparer le téléchargement APK**.
3. Vérifiez que les contrôles sont verts et que la comparaison vous convient.
4. Lorsque vous décidez de rendre la nouvelle page publique, cliquez sur **Merge pull request**, puis **Confirm merge**.

Cette fusion reste votre décision : elle n’est pas effectuée automatiquement par Codex. Si la demande de fusion est déjà ouverte, utilisez-la au lieu d’en créer une deuxième.

## 2. Récupérer les trois fichiers préparés

Utilisez les fichiers fournis avec le compte rendu de Codex. Dans le ZIP de publication, vous trouverez :

| Fichier | À quoi il sert |
| --- | --- |
| **`langue.apk`** | L’application Android installable, en version de test |
| **`RELEASE_NOTES.md`** | Le texte de présentation à copier dans la publication |
| **`SHA256SUMS.txt`** | Les empreintes permettant de vérifier les deux fichiers précédents |

Si vous utilisez plutôt les fichiers produits sur GitHub :

1. Ouvrez [les compilations Android](https://github.com/j-pirate/langue/actions/workflows/android.yml).
2. Ouvrez l’exécution réussie correspondant à votre version, avec une coche verte.
3. En bas, dans **Artifacts**, cliquez sur **langue-installation-test** et téléchargez le ZIP. Connectez-vous à GitHub si nécessaire.
4. Extrayez les trois fichiers de ce ZIP. Le fichier **`langue.apk`**, et non le ZIP, est celui à installer et à joindre à la publication.

L’artefact GitHub Actions est temporaire, conservé ici 90 jours. Une publication dans **Releases** sert à conserver le téléchargement durablement. Ne sélectionnez pas les artefacts `reports` ou `universal-debug-apk` pour cette procédure : le paquet nommé **langue-installation-test** contient les trois fichiers avec les bons noms.

## 3. Créer la publication de test

Préparez les trois fichiers ci-dessus sur votre ordinateur, puis :

1. Ouvrez [le formulaire de nouvelle publication](https://github.com/j-pirate/langue/releases/new). Connectez-vous avec le compte propriétaire du dépôt.
2. Cliquez sur **Choose a tag**, saisissez exactement **`v1.1.0-test.1`**, puis choisissez **Create new tag**. Dans **Target**, choisissez **main**, après la fusion de l’étape 1.
3. Dans **Release title**, saisissez exactement **Langue 1.1.0 — version de test**.
4. Ouvrez le fichier fourni **`RELEASE_NOTES.md`** et copiez tout son texte dans **Describe this release**. Gardez bien l’avertissement indiquant qu’il s’agit d’une version de test.
5. Dans la zone **Attach binaries by dropping them here or selecting them**, sélectionnez **`langue.apk`**, **`RELEASE_NOTES.md`** et **`SHA256SUMS.txt`**. Attendez que les trois téléversements soient terminés. Ne renommez pas `langue.apk` et ne joignez pas le ZIP à sa place.
6. Laissez **This is a pre-release** décoché et, si l’option est affichée, cochez **Set as latest release**. Ce réglage permet le lien direct permanent demandé pour la page d’accueil. **L’APK reste une version de test**, comme l’indiquent son titre et sa description ; ce réglage ne lui donne pas une signature de production.
7. Cliquez sur **Publish release**, et non sur **Save draft**.

Le tag contenant « test », le titre et les notes doivent rester explicites. Ne présentez pas cette APK comme définitive. Si vous préférez utiliser la case **This is a pre-release**, GitHub exclura cette publication du lien « latest » : il faudra alors utiliser son lien de version précis. Le parcours ci-dessus choisit une publication ordinaire clairement étiquetée **test** pour garder un téléchargement simple.

## 4. Vérifier que le téléchargement fonctionne

1. Ouvrez [la liste des publications](https://github.com/j-pirate/langue/releases). La publication **Langue 1.1.0 — version de test** doit apparaître comme publiée, avec les trois fichiers dans **Assets**.
2. Ouvrez [le lien permanent de l’APK](https://github.com/j-pirate/langue/releases/latest/download/langue.apk). Il doit télécharger directement **`langue.apk`** ; il ne doit pas ouvrir un rapport ou un ZIP de sources.
3. Sur votre téléphone Android, ouvrez l’APK téléchargée. Autorisez l’installation si Android le demande. L’application doit montrer son écran sans contenu et ses trois onglets.

Si le lien permanent affiche une erreur 404, vérifiez que la publication est publique, qu’elle n’est ni un brouillon ni marquée **pre-release**, qu’elle est définie comme la dernière publication et que la pièce jointe s’appelle exactement **langue.apk**.

## 5. Activer le lien visible dans la page d’accueil

Effectuez cette étape uniquement après le téléchargement réussi de l’étape 4.

1. Ouvrez [la modification du README](https://github.com/j-pirate/langue/edit/main/README.md).
2. Dans la partie **Télécharger l’application Android (APK)**, repérez les lignes `<!-- DOWNLOAD:START -->` et `<!-- DOWNLOAD:END -->`.
3. Remplacez uniquement le texte situé entre ces deux lignes par ce bloc :

```markdown
**[Télécharger l’application Android — APK de test](https://github.com/j-pirate/langue/releases/latest/download/langue.apk)**

Version de test pour les essais personnels. Aucune leçon n’est préinstallée.
```

4. Cliquez sur **Commit changes**, indiquez **Activer le téléchargement de Langue**, puis confirmez avec **Commit changes**. Si GitHub impose une demande de fusion, validez cette petite modification avec le parcours de l’étape 1.
5. Ouvrez [la page d’accueil du dépôt](https://github.com/j-pirate/langue) et cliquez sur le lien : le téléphone doit télécharger `langue.apk`.

Pour les versions suivantes, conservez le nom du fichier **langue.apk**, créez une nouvelle publication et marquez-la comme la dernière. Le lien de la page d’accueil ne change plus. Ne remplacez pas silencieusement le fichier d’une version déjà annoncée : utilisez un nouveau numéro et une nouvelle description.

## Avant une véritable version de production

Une clé de publication durable doit être sous votre contrôle et rester hors de GitHub. Une APK de test produite ailleurs peut avoir une autre signature ; Android pourra refuser sa mise à jour. Avant toute désinstallation, exportez la progression depuis l’application et conservez les packs, car les données locales seraient effacées.

Le paquet préparé ici ne contient aucune clé ni aucun mot de passe. Aucune publication Google Play n’est effectuée. La préparation technique d’une APK signée pour les utilisateurs est expliquée dans [GOOGLE_PLAY.md](GOOGLE_PLAY.md) et [VARIANTS.md](VARIANTS.md). La procédure de compilation et de vérification du paquet de test figure dans [DEVELOPMENT.md](DEVELOPMENT.md).
