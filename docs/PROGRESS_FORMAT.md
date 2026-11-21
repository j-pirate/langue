# Export de progression — schéma 1

Depuis **Paramètres**, exporter une sauvegarde JSON à l’emplacement choisi via le sélecteur Android. Le fichier contient la progression de tous les packs installés ; il ne contient ni mots, ni traductions, ni audios. Conserver également les ZIP des packs.

La progression reste dans le stockage privé de l’application. Supprimer un pack supprime aussi sa progression locale ; l’exporter auparavant si nécessaire. La réinitialisation confirmée du pack actif efface ses résultats sans retirer son contenu. La sauvegarde automatique Android est désactivée : l’export explicite reste sous votre contrôle. Conserver l’export et les ZIP originaux avant toute désinstallation.

Structure :

```json
{
  "schemaVersion": 1,
  "packs": []
}
```

Chaque objet du tableau `packs` contient :

| Champ | Description |
| --- | --- |
| `packId` | Identifiant stable du pack |
| `currentStreak` | Nombre de réponses correctes consécutives en cours |
| `bestStreak` | Record de réponses correctes consécutives |
| `answered` | Nombre total de réponses enregistrées |
| `correct` | Nombre de bonnes réponses |
| `viewedEntryIds` | Tableau des identifiants d’élément consultés |

Tous les compteurs sont des entiers positifs ou nuls. Les contraintes sont `correct <= answered`, `currentStreak <= bestStreak <= correct`. Aucun identifiant de pack ou d’élément ne doit être dupliqué dans son tableau.

Pour restaurer sur une installation vierge, installer d’abord tous les packs mentionnés dans la sauvegarde, puis sélectionner le JSON depuis les paramètres. Les identifiants consultés retirés d’une nouvelle version du pack sont ignorés. Les packs absents de la sauvegarde conservent leur progression ; ceux mentionnés reçoivent les valeurs sauvegardées. La restauration remplace ces valeurs, elle ne les additionne pas.

La lecture est bornée à 8 Mio de caractères, au plus 1 000 packs et 100 000 identifiants consultés par pack. Un format inconnu, des compteurs incohérents, des doublons ou un pack non installé provoquent le refus de la restauration sans modification partielle. L’import des sauvegardes utilise le même parseur JSON strict que les packs.

Ce format est ouvert et éditable, mais il convient d’utiliser en priorité un export produit par l’application. Une sauvegarde de progression ne remplace pas un pack et ne peut pas fournir du vocabulaire manquant.
