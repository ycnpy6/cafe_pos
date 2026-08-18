# Guide utilisateur — Common Grounds POS

Ce guide accompagne l'application de caisse Common Grounds. Chaque section
decrit une action precise, etape par etape, avec ce que vous devez voir a
l'ecran a chaque moment. Gardez-le pres de la caisse pendant les premiers
jours d'utilisation.

## Sommaire

1. [Premier lancement et connexion](#1-premier-lancement-et-connexion)
2. [Ecran de caisse (POS)](#2-ecran-de-caisse-pos)
3. [Encaisser une commande](#3-encaisser-une-commande)
4. [Imprimer un ticket ou une facture](#4-imprimer-un-ticket-ou-une-facture)
5. [Rembourser une commande](#5-rembourser-une-commande)
6. [Mettre une commande en attente](#6-mettre-une-commande-en-attente)
7. [Clients et cartes prepayees](#7-clients-et-cartes-prepayees)
8. [Retrait de caisse](#8-retrait-de-caisse)
9. [Gerer le stock](#9-gerer-le-stock)
10. [Consulter les rapports](#10-consulter-les-rapports)
11. [Parametres (reserve manager)](#11-parametres-reserve-manager)
12. [Verrouiller / cloturer la session](#12-verrouiller--cloturer-la-session)
13. [Depannage rapide](#13-depannage-rapide)

---

## 1. Premier lancement et connexion

**Ce que vous voyez :** l'ecran d'accueil "Common Grounds" avec trois gros
boutons — **Caisse**, **Stock**, **Gestion** — et un lien discret
**Parametres** en bas.

- **Caisse** : ouvre la caisse (necessite de se connecter).
- **Stock** : accessible directement, sans code, pour consulter et ajuster
  le stock (voir [section 9](#9-gerer-le-stock)).
- **Gestion** et **Parametres** : reserves au manager (code demande si
  necessaire).

### Se connecter a la caisse

1. Cliquez sur **Caisse**.
2. Choisissez votre role en haut de l'ecran : **Barista** ou **Manager**.
3. Selectionnez votre nom dans la liste deroulante.
4. Si un code est demande, saisissez-le au clavier ou au pave numerique a
   l'ecran.
5. Cliquez sur **Connexion**. L'ecran de caisse s'ouvre directement.

> Un barista peut ouvrir une session sans code. Un manager doit toujours
> saisir son code personnel.

---

## 2. Ecran de caisse (POS)

L'ecran de caisse est divise en trois zones :

| Zone | Contenu |
|---|---|
| **Gauche** (grande zone) | Grille des produits, avec une icone par article et son prix |
| **Droite (etroite)** | Categories, sous forme d'onglets verticaux avec icone (Boissons chaudes, Boissons froides, Sucreries, Sale...) |
| **Panneau droit** | Commande en cours : lignes ajoutees, total, boutons de paiement |
| **Bas de l'ecran** | Barre d'actions avec icones : Nouvelle commande, Annuler, Attente, Commandes en attente, Remise, Retrait, Remboursement, Reimpression, Historique, Recharge, Nouveau client, Facture, Verrouiller, Cloturer session |

### Ajouter un produit a la commande

1. Cliquez sur une categorie a droite (l'icone s'allume en marron fonce).
2. Cliquez sur la tuile du produit voulu. Il apparait immediatement dans le
   panneau de commande a droite, avec son prix.
3. Pour changer la quantite d'une ligne, utilisez les boutons **+/-** a
   cote de la ligne dans le panneau de commande.
4. Pour ajouter des options (lait, sucre, taille...), un clic long sur la
   tuile produit ouvre la liste des suppléments disponibles pour cette
   categorie ; cochez ce qu'il faut puis validez.

### Appliquer une remise

1. Cliquez sur le bouton **Remise** (icone %) dans la barre d'actions.
2. Choisissez le mode : **%** (pourcentage) ou **DZD** (montant fixe).
3. Saisissez la valeur. Le nouveau total s'affiche immediatement en
   apercu.
4. Validez. La ligne "Remise" apparait dans le recapitulatif de commande.

---

## 3. Encaisser une commande

Une fois la commande complete, deux boutons de paiement principaux sont
visibles dans le panneau de droite : **Especes** et **Prepaye**.

### Paiement especes

1. Cliquez sur **Especes**.
2. Une fenetre s'ouvre avec un pave numerique : saisissez le montant remis
   par le client.
3. La monnaie a rendre s'affiche automatiquement en bas de la fenetre.
4. Validez. Le ticket part automatiquement en impression (voir
   [section 4](#4-imprimer-un-ticket-ou-une-facture)) et la commande se
   vide pour la suivante.

### Paiement par carte prepayee (client RFID)

1. Un client doit d'abord etre associe a la commande : scannez sa carte
   sur le lecteur (le champ de saisie invisible capte automatiquement le
   scan), ou passez par **Nouveau client** si c'est sa premiere visite
   (voir [section 7](#7-clients-et-cartes-prepayees)).
2. Une fois le client reconnu, son nom et son solde s'affichent en haut du
   panneau de commande.
3. Cliquez sur **Prepaye**. Le montant total est debite automatiquement du
   solde du client — aucune saisie necessaire si le solde est suffisant.
4. Si le solde est insuffisant, l'application le signale : proposez un
   paiement mixte (voir ci-dessous) ou une recharge prealable.

### Paiement mixte (especes + prepaye)

Utilise quand le solde du client ne couvre pas la totalite : une partie du
montant est prelevee sur la carte, le reste est regle en especes dans la
meme transaction. L'ecran de paiement affiche les deux montants a saisir
et calcule automatiquement la repartition.

---

## 4. Imprimer un ticket ou une facture

- **Automatique** : chaque vente encaissee part automatiquement en
  impression (aucune action requise), a condition qu'une imprimante soit
  configuree (voir [section 11.1](#111-imprimante)).
- **Reimpression** : cliquez sur **Reimpression** dans la barre d'actions
  pour renvoyer le dernier ticket a l'imprimante, ou pour reimprimer une
  commande precise depuis l'historique (bouton **Reimprimer** sur chaque
  ligne).
- **Facture** (pour un client professionnel) : cliquez sur **Facture**
  dans la barre d'actions. Une fenetre s'ouvre avec le detail de la
  commande ; vous pouvez y saisir un nom et une adresse de destinataire.
  Deux boutons : **Apercu** (voir le contenu avant impression, avec choix
  de l'imprimante) et **Imprimer (thermique)** (envoi direct au ticket
  imprimante).

> Si aucune imprimante ticket (thermique) n'est branchee, utilisez
> **Apercu** puis choisissez une imprimante standard (ex: une imprimante
> de bureau) dans la liste — le document s'imprime alors comme un document
> classique.

---

## 5. Rembourser une commande

1. Cliquez sur **Remboursement** dans la barre d'actions.
2. Recherchez la commande a rembourser (par numero, ou dans la liste des
   commandes recentes).
3. Cochez les lignes/quantites a rembourser (un remboursement partiel
   d'une seule ligne est possible).
4. Choisissez le mode de remboursement : **Especes** ou **Remise sur la
   carte** (si le client a une carte prepayee).
5. Ajoutez une raison si demande, puis validez.

Le stock des produits/ingredients concernes est automatiquement remis a
jour (restitue).

---

## 6. Mettre une commande en attente

Utile si un client change d'avis en cours de commande, ou pour liberer la
caisse pendant qu'un autre client patiente.

1. Composez la commande normalement.
2. Cliquez sur **Attente** dans la barre d'actions. La commande est
   sauvegardee et l'ecran de caisse redevient vide.
3. Pour la reprendre : cliquez sur **Commandes en attente** (icone liste),
   retrouvez la commande dans le panneau qui s'ouvre, puis cliquez sur
   **Reprendre**. La commande revient exactement comme elle etait.
4. Le bouton **Commandes en attente** affiche un badge avec le nombre de
   commandes en attente, visible depuis la barre de statut en bas de
   l'ecran.

---

## 7. Clients et cartes prepayees

### Associer une carte a un nouveau client

1. Cliquez sur **Nouveau client** dans la barre d'actions (ou depuis
   l'ecran Clients).
2. Scannez la carte RFID du client dans le champ prevu (ou saisissez son
   identifiant manuellement).
3. Renseignez son nom.
4. Validez. Le client est cree, sans solde par defaut.

### Recharger le solde d'un client

1. Associez d'abord le client a la commande en cours (scan de sa carte).
2. Cliquez sur **Recharge** dans la barre d'actions.
3. Saisissez le montant a ajouter (minimum 100 DZD).
4. Validez. Le nouveau solde s'affiche immediatement.

### Importer une liste de clients (CSV)

Voir le fichier separe `clients_template.csv` et ses instructions
(`clients_template_LISEZMOI.txt`) fournis avec l'installation :
Ecran **Clients → Importer CSV**, puis selectionnez votre fichier prepare
sous Excel.

---

## 8. Retrait de caisse

Pour sortir des especes de la caisse (depense, remise en banque...) :

1. Cliquez sur **Retrait** dans la barre d'actions.
2. Le montant actuellement attendu en caisse s'affiche a titre indicatif.
3. Saisissez le montant a retirer et une raison.
4. Validez. Le retrait est enregistre et apparait dans les rapports de fin
   de journee.

---

## 9. Gerer le stock

**Accessible a tous, sans code**, depuis l'ecran d'accueil (**Stock**) ou
depuis la caisse (bouton **Stock** dans la navigation).

### Consulter le stock

L'ecran Stock liste les produits et ingredients avec leur quantite
actuelle. Un badge d'avertissement (!) apparait sur les articles dont le
stock est bas.

### Ajuster une quantite (barista)

1. Repérez la ligne du produit ou de l'ingredient concerne.
2. Utilisez les boutons **+/-** pour corriger la quantite (casse, perte,
   recomptage), ou saisissez directement la nouvelle quantite dans la
   case prevue.
3. Le changement est enregistre immediatement et trace dans l'historique
   des mouvements de stock.

### Actions reservees au manager

Depuis le meme ecran Stock, certaines actions demandent le code manager
la premiere fois (puis restent actives 5 minutes sans redemander) :
modifier le **prix** ou le **cout** d'un produit, editer une **recette**,
gerer les **ingredients** en detail, enregistrer un **achat
d'ingredient**, ou modifier une **categorie**.

---

## 10. Consulter les rapports

**Depuis Gestion → Rapports** (code manager requis).

- **Resume du jour** : chiffre d'affaires, nombre de commandes,
  encaissements especes/prepaye, cout ingredients, marge, retraits, revenu
  net.
- **Top produits**, **Historique des commandes**, **Mouvements de
  caisse**.
- **Export Excel** : chaque soir, un classeur `.xls` mis en forme
  (couleurs de la marque) est genere automatiquement dans le dossier
  configure en Parametres, avec un dossier par jour et un fichier
  hebdomadaire recapitulatif. Si ce dossier est un dossier Dropbox ou
  reseau partage, les rapports sont consultables a distance sans rien
  faire de plus.

---

## 11. Parametres (reserve manager)

### 11.1 Imprimante

1. Ouvrez **Parametres → Impression & ticket**.
2. Selectionnez votre imprimante dans la liste, cliquez sur
   **Enregistrer**.
3. Cliquez sur **Apercu** pour voir un exemple de ticket et l'imprimer
   directement pour tester, sur l'imprimante thermique ou une imprimante
   standard.

### 11.2 Format du ticket

Personnalisez le nom de la boutique, le telephone, le titre du ticket, la
devise, le pied de page, et si le bloc client doit apparaitre sur le
ticket.

### 11.3 Utilisateurs et codes

1. **Ajouter un utilisateur** : renseignez son nom, son code (PIN), son
   role (Barista/Manager), cliquez sur **Ajouter**.
2. **Changer un code** : selectionnez l'utilisateur dans la liste, cliquez
   sur **Changer PIN**, saisissez le nouveau code deux fois sur le pave
   numerique (4 a 6 chiffres). Fonctionne aussi pour changer le code du
   manager/administrateur.
3. **Supprimer un utilisateur** : selectionnez-le puis **Supprimer** (le
   dernier compte manager ne peut pas etre supprime).

### 11.4 Actions et acces

Tableau qui liste chaque action sensible de l'application (ouvrir le
stock, modifier un prix, faire un retrait...) avec deux reglages
independants :

- **Role min** : qui peut faire cette action (Barista ou Manager).
- **PIN requis** : si coche, redemande toujours le code meme si la
  personne est deja connectee en tant que manager — utile pour les
  actions les plus sensibles que vous voulez reconfirmer a chaque fois.

### 11.5 Sauvegarde et restauration

- **Sauvegarder maintenant** : cree une copie de la base de donnees dans
  le dossier choisi. Une sauvegarde automatique se fait aussi chaque nuit.
- **Choisir lecteur** : pointez vers un dossier externe (cle USB, dossier
  reseau) pour que les sauvegardes y soient egalement copiees chaque nuit.
- **Restaurer depuis fichier** : en cas de probleme, selectionnez un
  fichier de sauvegarde `.db` ; l'application redemarre avec ces donnees.

### 11.6 Dossier d'export

Definissez ou les rapports quotidiens/hebdomadaires sont ecrits. Pointez
vers un dossier Dropbox/OneDrive/reseau pour un acces a distance. Un
avertissement rouge apparait dans cet ecran tant qu'aucun dossier n'est
choisi.

### 11.7 Langue

Choisissez Francais ou English, cliquez sur **Enregistrer langue**, puis
redemarrez l'application pour appliquer le changement partout.

---

## 12. Verrouiller / cloturer la session

Deux boutons distincts dans la barre d'actions de la caisse :

- **Verrouiller** : revient a l'ecran d'accueil sans fermer la journee.
  La commande en cours est conservee ; on peut se reconnecter et
  continuer.
- **Cloturer session** *(fin de journee, action manager)* : ferme
  definitivement le tiroir-caisse pour la journee. Une confirmation est
  demandee, avec le choix d'imprimer un ticket recapitulatif complet de
  la journee (chiffre d'affaires, marge, retraits, revenu net) avant la
  deconnexion.

> Meme sans clic sur "Cloturer session", l'application effectue une
> cloture automatique chaque nuit a 23h55, et rattrape le jour precedent
> au demarrage si le poste etait eteint a cette heure-la.

---

## 13. Depannage rapide

| Probleme | A verifier |
|---|---|
| Le ticket ne s'imprime pas | Parametres → Impression : l'imprimante est-elle bien selectionnee ? Testez avec **Apercu**. Verifiez qu'elle a du papier et qu'elle est allumee. |
| Le PIN est refuse | Verifiez le role selectionne (Barista/Manager) correspond au compte. En cas d'oubli du code manager, un autre manager peut le reinitialiser depuis Parametres → Utilisateurs. |
| Un ecran demande un code alors qu'il ne devrait pas | Parametres → Actions et acces : verifiez le "Role min" de l'action concernee. |
| Les rapports ne sont pas visibles a distance | Parametres → Exports : un dossier Dropbox/reseau est-il bien configure et synchronise ? |
| L'application ne demarre pas | Contactez le support technique (numero disponible dans Parametres → Assistance). |

---

*Ce guide couvre les operations courantes. Pour toute question non
traitee ici, contactez le support technique Common Grounds (numero
disponible dans l'application, Parametres → Contact technique).*
