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

## Publication

Une APK `Debug` est signée par une clé de développement et sert aux essais. Pour distribuer une version :

1. Choisir l’identifiant définitif, l’icône, les textes, le pack et les métadonnées de boutique.
2. Définir les numéros de version Android (`versionCode` croissant, `versionName`) dans la configuration Gradle. Ils sont indépendants de la version du pack.
3. Configurer une signature de publication sous votre contrôle via Android Studio **Generate Signed App Bundle / APK**, ou une configuration Gradle sécurisée locale. Ne jamais versionner le keystore ni son mot de passe.
4. Produire une APK signée ou un AAB dédié, puis vérifier l’installation, le premier lancement sans réseau et le contenu réellement embarqué.
5. Conserver la clé de signature : elle est indispensable aux futures mises à jour Android.

La commande `:app:assembleDedicatedRelease` compile une sortie Release avec les mêmes propriétés ; sans configuration de signature de publication, cette sortie n’est pas une APK publiable signée. `:app:bundleDedicatedRelease` produit un AAB à signer pour une boutique compatible. Le projet ne crée pas de comptes de publication et ne publie rien automatiquement.

Les ZIP pédagogiques peuvent évoluer séparément de l’APK. Maintenir des identifiants d’éléments stables pour préserver la progression. Un changement de langue ne nécessite aucune réécriture Kotlin.
