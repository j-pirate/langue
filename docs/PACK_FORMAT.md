# Format des packs — schéma 1

Un pack est une archive ZIP UTF-8 contenant exactement les trois fichiers JSON de structure et les fichiers audio locaux facultatifs. Il peut avoir ces fichiers à la racine ou dans **un seul dossier enveloppe** :

```text
mon-pack/
  manifest.json
  categories.json
  entries.json
  audio/
    fichier.mp3
```

Ne pas ajouter de fichiers cachés, `__MACOSX`, README, images ou JSON supplémentaires au ZIP : ils sont refusés. Le dossier `audio/` est facultatif et peut contenir des sous-dossiers sûrs. Les clés JSON inconnues sont refusées, pour détecter notamment les erreurs de saisie. Les commentaires et virgules finales ne sont pas acceptés.

## `manifest.json`

| Champ | Type | Règle |
| --- | --- | --- |
| `schemaVersion` | entier | `1` ; valeur par défaut si omis |
| `id` | chaîne | Identifiant unique et stable du pack |
| `name` | chaîne | Nom lisible du pack |
| `version` | entier | Au moins `1`, strictement croissant à chaque mise à jour |
| `sourceLanguage` | objet langue | Langue connue par l’utilisateur |
| `targetLanguage` | objet langue | Langue étudiée |
| `description` | chaîne facultative | Description générale, au plus 10 000 caractères |

Un objet langue contient `tag` (code BCP 47 structurel, par exemple `fr` ou `en-GB`), `name` (nom affiché) et `direction` facultatif parmi `auto`, `ltr`, `rtl` ; `auto` est la valeur par défaut. Le moteur ne contient pas de liste de langues. La validation vérifie la forme du code, pas son inscription dans un registre linguistique.

Exemple de **métadonnées pour un pack vide**, sans vocabulaire fourni :

```json
{
  "schemaVersion": 1,
  "id": "mon-pack",
  "name": "Mon carnet",
  "version": 1,
  "sourceLanguage": { "tag": "fr", "name": "Français", "direction": "ltr" },
  "targetLanguage": { "tag": "en", "name": "Anglais", "direction": "ltr" }
}
```

Ces langues sont uniquement un exemple de métadonnées : remplacer les deux objets par la combinaison souhaitée. Un pack vide est valide ; il ne fournit aucun quiz.

## `categories.json`

Tableau d’objets catégorie, ou `[]` pour un pack vide.

| Champ | Type | Règle |
| --- | --- | --- |
| `id` | chaîne | Identifiant stable, unique dans le pack |
| `name` | chaîne | Nom dans la langue source |
| `targetName` | chaîne facultative | Nom dans la langue cible |
| `icon` | chaîne facultative | Symbole/emoji affiché comme texte, au plus 32 caractères ; pas un chemin de fichier |
| `order` | entier facultatif | Entier positif ou nul, `0` par défaut ; ordre d’affichage |

Pour ajouter une catégorie, ajouter un objet dans ce tableau. Pour changer son nom ou son ordre, conserver son `id`.

## `entries.json`

Tableau d’objets vocabulaire, ou `[]` pour un pack vide.

| Champ | Type | Règle |
| --- | --- | --- |
| `id` | chaîne | Identifiant stable, unique dans le pack |
| `categoryId` | chaîne | Identifiant d’une catégorie existante |
| `source` | chaîne | Texte source, mot ou expression |
| `target` | chaîne | Traduction principale |
| `alternatives` | tableau de chaînes facultatif | Autres traductions acceptées ; `[]` par défaut, au plus 100 variantes |
| `transliteration` | chaîne facultative | Transcription phonétique ou translittération |
| `audio` | chaîne facultative | Chemin relatif tel que `audio/fichier.mp3` |

Pour ajouter du vocabulaire, ajouter les éléments réels dans ce tableau et renseigner `categoryId`. Tous les éléments restent propres à leur pack. Préserver les identifiants même si les textes évoluent. Ne jamais réutiliser un identifiant retiré pour un concept différent.

Les chaînes sont conservées en Unicode pour l’affichage. Les écritures de droite à gauche sont prises en charge ; renseigner `direction: "rtl"` pour imposer la direction d’une langue. La recherche normalise la casse, les marques diacritiques et les espaces, mais ne réécrit pas le texte affiché.

## Audio

Copier les enregistrements dans `audio/` et renseigner `audio` pour chaque élément concerné. Extensions acceptées : **MP3, M4A, AAC, WAV, OGG, FLAC**. Les formats et codecs réellement lisibles dépendent des décodeurs Android de l’appareil ; une extension autorisée n’est pas une certification du flux audio.

Sans enregistrement, omettre `audio` : l’interface indique l’indisponibilité et reste utilisable. Une référence vers un fichier absent provoque le refus du pack. Un fichier corrompu est signalé lors de la lecture ; l’import ne décode pas tous les enregistrements. Ne pas utiliser d’URL, de chemin absolu ni de fichier à l’extérieur du pack.

## Identifiants, textes et limites

Les identifiants de pack, catégorie et élément respectent `[A-Za-z0-9][A-Za-z0-9._-]{0,127}` : de 1 à 128 caractères ASCII. Les noms, textes et variantes doivent être non vides et comporter au plus 2 000 caractères, sauf limites spécifiques ci-dessus. Les champs facultatifs textuels doivent être omis plutôt que fournis vides. Les caractères NUL sont interdits.

| Limite | Valeur maximale |
| --- | --- |
| Archive compressée lue | 64 Mio |
| Ensemble des fichiers extraits | 128 Mio |
| Un fichier extrait | 32 Mio |
| Un fichier JSON | 8 Mio |
| Entrées ZIP, dossiers compris | 5 000 |
| Catégories déclarées | 5 000 |
| Éléments déclarés | 50 000 |
| Longueur d’un chemin ZIP | 240 caractères |

L’import refuse les traversées de chemin (`..`), chemins absolus, antislashs, deux-points, caractères de contrôle, chemins ambigus/dupliqués y compris après normalisation Unicode et casse, références de catégorie inexistantes, identifiants dupliqués et versions de schéma inconnues. L’extraction est temporaire et bornée ; les fichiers existants ne sont jamais écrasés par un import invalide.

## Créer et valider l’archive

Depuis le dossier contenant `manifest.json`, `categories.json` et `entries.json` :

```sh
zip -r ../mon-pack.zip manifest.json categories.json entries.json
# Ajouter audio/ uniquement si ce dossier contient vos enregistrements.
zip -r ../mon-pack.zip audio/
```

La seconde commande est facultative. L’outil ZIP met à jour une archive existante : pour éviter de conserver d’anciens fichiers, repartir d’une nouvelle archive lorsque sa structure change.

Depuis la racine du projet Android :

```sh
./gradlew :engine:run --args="/chemin/absolu/mon-pack.zip"
```

Distribuer uniquement une archive validée, puis la sélectionner depuis les paramètres Android de l’application. Aucun hébergement ni serveur de packs n’est requis.

## Mise à jour et quiz

Pour mettre un pack à jour, conserver son `id`, augmenter `version` et préserver les codes de langue source/cible. Importer le nouveau ZIP par la même fonction d’importation ; une version égale ou inférieure est refusée. La mise à jour conserve les statistiques de quiz et les consultations des identifiants d’élément encore présents. Les consultations d’éléments supprimés sont retirées. Une modification des langues doit utiliser un nouvel identifiant de pack.

Les questions viennent des éléments importés. Pour produire un quiz, il faut au moins trois groupes de réponses distincts. Les traductions principales/alternatives équivalentes et les textes source identiques forment des groupes transitifs après normalisation ; deux choix du même groupe ne sont pas opposés. Les équivalences sémantiques non indiquées dans les données ne peuvent pas être devinées par le moteur : renseigner les variantes et vérifier la cohérence éditoriale du pack.

`schemaVersion` décrit le format, tandis que `version` décrit l’édition d’un pack particulier. Un changement incompatible de structure exige une nouvelle version de schéma et une prise en charge explicite dans l’application ; ne pas seulement augmenter `version` pour modifier les noms des champs.
