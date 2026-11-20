# Variantes autonomes et publication

Les variantes `universal` et `dedicated` partagent le moteur, les écrans et la persistance. La variante universelle démarre sans contenu ; la variante dédiée embarque le ZIP fourni à la compilation. Elle reste compatible avec l’import et la gestion de packs.

## Construire une APK dédiée

Préparer un pack personnel conforme à [PACK_FORMAT.md](PACK_FORMAT.md), puis le valider avant compilation :

```sh
./gradlew :engine:run --args="/chemin/absolu/mon-pack.zip"
./gradlew :app:assembleDedicatedDebug \
  -PdedicatedPack=/chemin/absolu/mon-pack.zip \
  -PdedicatedId=org.monorganisation.moncarnet \
  -PdedicatedName="Mon carnet" \
  -PdedicatedSubtitle="Apprendre à mon rythme" \
  -PdedicatedAbout="Une application éditée par mon organisation." \
  -PdedicatedAccent="#A44836"
```

Sortie : `app/build/outputs/apk/dedicated/debug/app-dedicated-debug.apk`. Le ZIP est copié dans les assets générés de la seule variante dédiée ; il n’est pas nécessaire de le versionner ni de le placer dans `app/src/main/assets/`. La tâche de compilation dédiée valide elle-même le ZIP avec le moteur avant de l’embarquer. Une compilation dédiée sans fichier de pack ou avec un pack invalide échoue volontairement.

Au premier démarrage, le pack est importé depuis l’APK, sans connexion. L’utilisateur peut le supprimer ; il n’est pas réinstallé au prochain lancement. Un nouveau pack embarqué dans une mise à jour de l’APK est détecté par son empreinte SHA-256 et importé une seule fois. Il doit conserver son identifiant et augmenter sa version ; le même mécanisme de mise à jour est aussi disponible depuis les paramètres.

| Propriété Gradle | Valeur par défaut | Effet |
| --- | --- | --- |
| `dedicatedPack` | Aucune, obligatoire pour construire | Chemin d’un ZIP local |
| `dedicatedId` | `org.langue.dedicated` | Identifiant Android, à rendre unique pour chaque application distribuée |
| `dedicatedName` | `Langue dédiée` | Nom Android et titre |
| `dedicatedSubtitle` | `Votre carnet de langues.` | Sous-titre |
| `dedicatedAbout` | `Apprendre à son rythme, hors ligne.` | Présentation |
| `dedicatedAccent` | `#A44836` | Couleur d’accent Android |
| `dedicatedBrandingDir` | Aucun | Dossier de ressources Android spécifiques à la variante |

Le nom et les textes sont traités comme des chaînes Android littérales ; les apostrophes, esperluettes et guillemets sont échappés automatiquement. Vérifier la compilation après personnalisation. L’identifiant doit suivre les règles Android et rester stable pour publier une mise à jour de la même application. Des identifiants distincts créent des applications et stockages distincts, installables côte à côte.

## Icône et ressources

Créer un dossier externe de ressources Android avec, par exemple, `drawable/ic_launcher.xml` ou des variantes bitmap de même nom. Passer ensuite :

```sh
./gradlew :app:assembleDedicatedDebug \
  -PdedicatedPack=/chemin/absolu/mon-pack.zip \
  -PdedicatedBrandingDir=/chemin/absolu/identite/res
```

Ce dossier correspond au niveau `res/`, pas à son parent. Ses ressources sont ajoutées au source set `dedicated` et peuvent remplacer l’icône de base. Utiliser des ressources originales et des enregistrements dont vous détenez les droits. Aucune ressource d’une application tierce n’est fournie.

## APK universelles et sorties Release

Pour essayer l’application universelle vide :

```sh
./gradlew :app:assembleUniversalDebug
```

Les tâches `assemble` produisent des APK ; les tâches `bundle` produisent des AAB pour une distribution par une boutique compatible. Un AAB ne peut pas être installé directement avec `adb install`.

| Variante et tâche | Fichier produit |
| --- | --- |
| `:app:assembleUniversalDebug` | `app/build/outputs/apk/universal/debug/app-universal-debug.apk` |
| `:app:assembleDedicatedDebug` | `app/build/outputs/apk/dedicated/debug/app-dedicated-debug.apk` |
| `:app:assembleUniversalRelease` sans signature propriétaire | `app/build/outputs/apk/universal/release/app-universal-release-unsigned.apk` |
| `:app:assembleDedicatedRelease` sans signature propriétaire | `app/build/outputs/apk/dedicated/release/app-dedicated-release-unsigned.apk` |
| `:app:assembleUniversalRelease` avec signature configurée | `app/build/outputs/apk/universal/release/app-universal-release.apk` |
| `:app:assembleDedicatedRelease` avec signature configurée | `app/build/outputs/apk/dedicated/release/app-dedicated-release.apk` |
| `:app:bundleUniversalRelease` | `app/build/outputs/bundle/universalRelease/app-universal-release.aab` |
| `:app:bundleDedicatedRelease` | `app/build/outputs/bundle/dedicatedRelease/app-dedicated-release.aab` |

Les tâches dédiées nécessitent les propriétés de pack et d’identité décrites plus haut. Les noms de sortie AAB sont les mêmes avec ou sans signature : la présence du fichier ne prouve pas qu’il est signé.

## Version et signature de publication

Les propriétés `-PappVersionCode=2` et `-PappVersionName=1.0.1` configurent les numéros Android pour les deux variantes, sans modifier le code Kotlin. Les valeurs par défaut sont respectivement `1` et `1.0.0`. Le code doit être strictement positif et augmenter pour chaque mise à jour envoyée du même identifiant Android ; le nom doit être non vide. Ces numéros sont indépendants de la `version` du pack.

Le build accepte une signature Release locale ou CI via **quatre variables d’environnement**, toutes présentes et non vides : `LANGUE_KEYSTORE_FILE`, `LANGUE_KEYSTORE_PASSWORD`, `LANGUE_KEY_ALIAS` et `LANGUE_KEY_PASSWORD`. Une configuration partielle est refusée. Le propriétaire fournit sa clé de téléversement existante depuis un emplacement hors du dépôt et ses secrets par une saisie sécurisée. Aucune clé n’est créée par les commandes du projet ; aucune valeur ne doit être imprimée, versionnée ou ajoutée à la ligne de commande.

Sans ces quatre variables, les sorties Release sont non signées. Une APK Debug reste un fichier d’essai signé par une clé de développement. Ces fichiers ne sont pas des livrables Google Play prêts à publier.

Après configuration sécurisée de la signature :

```sh
./gradlew :app:bundleUniversalRelease \
  -PappVersionCode=2 -PappVersionName=1.0.1

./gradlew :app:bundleDedicatedRelease \
  -PappVersionCode=2 -PappVersionName=1.0.1 \
  -PdedicatedPack=/chemin/absolu/mon-pack.zip \
  -PdedicatedId=org.monorganisation.moncarnet \
  -PdedicatedName="Mon carnet"
```

Remplacer `bundle` par `assemble` pour une APK Release locale avec les mêmes propriétés. Vérifier le livrable final, son identité et sa signature, puis l’installation, la progression et les fichiers audio hors ligne sur appareil.

## Dépôt Google Play

Le [guide Google Play](GOOGLE_PLAY.md) détaille la saisie sécurisée de la signature, Play App Signing, les sorties AAB, la fiche de boutique et les déclarations de confidentialité. Le projet cible actuellement l’API 35 ; le propriétaire doit vérifier la [politique officielle de niveau d’API cible](https://support.google.com/googleplay/android-developer/answer/11926878) et adapter puis retester le projet si le niveau exigé au moment du dépôt est supérieur. Aucun résultat de compilation ne garantit à lui seul la conformité aux règles actuelles.

La publication reste manuelle dans le compte Play Console du propriétaire. Le projet ne publie rien automatiquement et ne fournit pas de fausses coordonnées ni de lien de politique de confidentialité. Les ZIP pédagogiques peuvent évoluer séparément de l’APK ; maintenir des identifiants d’éléments stables pour préserver la progression.
