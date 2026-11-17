Toutes nos modifications portent sur le module `tika-core`.
Le workflow modifié est `.github/workflows/main-jdk17-build.yml`.


---

### 1.Pourquoi modifier le workflow existant plutôt qu'en créer un nouveau

Nous avons ajouté un job semgrep dans le workflow de build existant plutôt que de créer un workflow séparé, pour trois raisons.

- La consigne demande que le processus de build échoue. Avec un workflow séparé, Semgrep échouerait de son côté, mais le workflow de build resterait vert et continuerait de compiler et tester le code vulnérable. En plaçant Semgrep dans le même workflow, avec needs semgrep sur le job build, une nouvelle vulnérabilité fait échouer tout le workflow et empêche le build de démarrer.
- Le développeur voit un seul statut pour son commit, au lieu de deux workflows à surveiller.

Le désavantage est que ce workflow mélange maintenant deux responsabilités, l'analyse de sécurité et le build. Nous l'avons accepté car le job semgrep reste séparé.


### Choix de conception

Le choix principal est de comparer chaque commit à un commit de référence plutôt que d'analyser tout le code. Le module tika-core contient déjà 37 findings avant nos modifications. Un scan complet ferait donc échouer le build à chaque commit, sauf si on ajoutait des exceptions pour ces findings. Ces exceptions ont des inconvénients. Les commentaires nosemgrep demandent de modifier 37 lignes dans une dizaine de fichiers de Tika qui ne sont pas liés à la tâche. Un fichier .semgrepignore ou l'exclusion de règles avec --exclude-rule masquent aussi les futures vulnérabilités, car un fichier ou une règle exclus ne sont plus analysés. Enfin, ces exceptions doivent être mises à jour à la main quand le code ou les règles changent.

Nous avons donc retenu un scan différentiel avec l'option --baseline-commit. Pour un push, le commit de référence est le dernier commit avant le push. Pour une pull request, c'est le commit de base de la branche cible. Semgrep fait échouer le job seulement si un finding apparaît par rapport à ce commit de référence.

Cette approche a une limite. Sur un push, si un commit vulnérable est poussé puis suivi d'un autre push sans correction, la référence devient le commit vulnérable et le job redevient vert. Pour une pull request, la référence reste la branche cible, donc la vulnérabilité continue de bloquer tant qu'elle n'est pas corrigée. C'est pourquoi le workflow s'exécute aussi sur les pull requests. Nous avons envisagé de comparer chaque push à la branche main. Nous avons gardé le commit précédent le push, car la consigne vise la vulnérabilité introduite par un commit, et cette option permet de montrer que chaque commit introduit exactement une nouvelle vulnérabilité.

### Choix d'implémentation

-Le job semgrep s'exécute en quatre étapes.

D'abord, il récupère le code du dépôt avec tout son historique, grâce à l'option fetch-depth 0. Par défaut, GitHub ne télécharge que le dernier commit, ce qui ne suffit pas pour comparer avec une version précédente.

Puis, il choisit le commit de référence. Pour une pull request, c'est le commit de base de la branche cible. Pour un push, c'est le commit qui était présent avant le push. Au premier push d'une nouvelle branche, GitHub ne fournit pas de commit précédent, alors nous utilisons le commit parent.

Enfin, il lance Semgrep sur tika-core avec les ensembles de règles p/java, p/security-audit et p/findsecbugs. Nous avons combiné trois ensembles car aucun ne détecte seul les sept types de vulnérabilités. L'option --baseline-commit limite le résultat aux nouveaux findings, et l'option --error fait échouer le job si au moins un nouveau finding est trouvé.

Le job build ne démarre que si le job semgrep réussit, grâce à needs semgrep. Nous avons aussi limité le build à tika-core.

1.5 Validation
Test	Résultat attendu	Résultat obtenu	Lien
Commit sans vulnérabilité	semgrep vert, build vert	Conforme	run #17
Commit avec une vulnérabilité (push)	semgrep rouge avec 1 finding, build non lancé	Conforme. Le log indique Scan was limited to files changed since baseline commit	run #4, vulnérabilité 1. Les runs des 6 autres sont en section 2
Pull request de tache3 vers main	semgrep rouge avec les 7 vulnérabilités	Conforme. 22 findings dans les 7 fichiers modifiés. Les 4 findings déjà présents dans NetworkParser et TemporaryResources ne sont pas comptés	pull request #1 et run #11

Chaque vulnérabilité de la section 2 a été poussée dans son propre commit, et chaque run correspondant est rouge sur le job semgrep.

La pull request a aussi déclenché d'autres workflows d'origine de Tika qui ciblent la branche main. Nous les avons annulés car ils ne font pas partie de notre modification. La pull request a été fermée sans fusion.

2. Vulnérabilités introduites
2.1 Résumé

Chaque vulnérabilité est une nouvelle méthode ajoutée dans une classe existante. Le code existant n'a pas été modifié. Avant chaque commit, nous avons vérifié en local que Semgrep détecte la faille avec les mêmes règles que la CI. Chaque vulnérabilité a été poussée dans son propre commit.

#	Type	Fichier	Findings
1	Injection de commande, CWE-78	utils/ProcessUtils.java	1
2	Hachage faible MD5, CWE-328	digest/DigestHelper.java	2
3	Désérialisation non sécurisée, CWE-502	config/ConfigDeserializer.java	1
4	Validation SSL désactivée, CWE-295	parser/NetworkParser.java	9
5	Permissions de fichier trop larges, CWE-732	io/TemporaryResources.java	2
6	Chiffrement faible DES en mode ECB, CWE-327	utils/StringUtils.java	5
7	Aléatoire prévisible, CWE-330	io/FilenameUtils.java	2
2.2 Vulnérabilité 1, Injection de commande
Fichier. tika-core/src/main/java/org/apache/tika/utils/ProcessUtils.java
Type. Injection de commande système
CWE. CWE-78
Règle Semgrep. java.lang.security.audit.command-injection-formatted-runtime-call
Ligne détectée. 301
Run. run #4, semgrep en échec et build non lancé

Code introduit.

java
public static Process runShellCommand(String userInput) throws IOException {
    return java.lang.Runtime.getRuntime().exec("sh -c ls " + userInput);
}

Choix du fichier. Nous avons ajouté la méthode runShellCommand dans ProcessUtils. Cette classe sert déjà à lancer des programmes externes et des commandes système, par exemple avec checkCommand. Ajouter une méthode qui exécute une commande shell y est donc réaliste. C'est exactement le genre d'endroit où un développeur pourrait introduire cette faille par erreur.

Explication. La valeur userInput est collée directement dans la commande avec l'opérateur plus, sans aucune vérification. Comme la commande est exécutée par un shell avec sh -c, les caractères spéciaux comme le point-virgule sont interprétés. Un attaquant qui contrôle userInput peut donc ajouter ses propres commandes, par exemple pour afficher des fichiers privés ou les supprimer avec rm -rf, et elles seront exécutées avec les droits de l'application.

Remarque. Notre première version utilisait Runtime sans nom complet et n'était pas détectée. La règle vérifie que l'objet appelé est de type java.lang.Runtime, et Semgrep ne le reconnaît que si ce nom apparaît dans le fichier. Nous avons d'abord ajouté import java.lang.Runtime, mais checkstyle de Tika refuse les imports du package java.lang. Nous avons donc écrit le nom complet java.lang.Runtime dans l'appel. Cela montre que Semgrep fait une analyse par motifs et ne résout pas les types comme un compilateur.

2.3 Vulnérabilité 2, Hachage faible MD5
Fichier. tika-core/src/main/java/org/apache/tika/digest/DigestHelper.java
Type. Utilisation d'un algorithme de hachage faible
CWE. CWE-328
Règle Semgrep. gitlab.find_sec_bugs.WEAK_MESSAGE_DIGEST_MD5-1 et java.lang.security.audit.crypto.use-of-md5
Ligne détectée. 113
Run. run #5, semgrep en échec et build non lancé

Code introduit.

java
public static byte[] quickDigest(byte[] data) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    return md.digest(data);
}

Choix du fichier. Nous avons ajouté la méthode quickDigest dans DigestHelper. Le package digest de Tika calcule les empreintes des documents, qui servent à vérifier qu'un fichier n'a pas été modifié et à repérer les doublons. DigestHelper organise déjà ce calcul.

Explication. Une empreinte sert à vérifier qu'un fichier n'a pas été modifié. MD5 n'est plus résistant aux collisions depuis 2004. On sait fabriquer deux fichiers différents qui ont la même empreinte. Un attaquant peut donc remplacer un document par un document malveillant sans que l'empreinte change, et la vérification ne détecte rien. Une seule faille produit ici deux findings, car deux règles différentes la détectent.

2.4 Vulnérabilité 3, Désérialisation non sécurisée
Fichier. tika-core/src/main/java/org/apache/tika/config/ConfigDeserializer.java
Type. Désérialisation de données non fiables
CWE. CWE-502
Règle Semgrep. java.lang.security.audit.object-deserialization
Ligne détectée. 138
Run. run #6, semgrep en échec et build non lancé

Code introduit.

java
public static Object readSerializedConfig(InputStream in)
        throws IOException, ClassNotFoundException {
    ObjectInputStream ois = new ObjectInputStream(in);
    return ois.readObject();
}

Choix du fichier. Nous avons ajouté la méthode readSerializedConfig dans ConfigDeserializer. Cette classe sert déjà à reconstruire la configuration de Tika à partir de données externes. Lire une configuration sérialisée en Java y est donc réaliste.

Explication. readObject reconstruit n'importe quel objet décrit dans le flux, sans vérifier sa classe. Pendant cette reconstruction, Java exécute automatiquement du code de certaines classes. Un attaquant qui contrôle le flux peut enchaîner ces exécutions pour lancer son propre code sur la machine.

2.5 Vulnérabilité 4, Validation SSL désactivée
Fichier. tika-core/src/main/java/org/apache/tika/parser/NetworkParser.java
Type. Validation incorrecte des certificats
CWE. CWE-295
Règle Semgrep. gitlab.find_sec_bugs.WEAK_TRUST_MANAGER, gitlab.find_sec_bugs.WEAK_HOSTNAME_VERIFIER-1.WEAK_TRUST_MANAGER-1 et java.lang.security.audit.crypto.ssl.insecure-trust-manager
Ligne détectée. 178 à 189
Run. run #7, semgrep en échec et build non lancé

Code introduit.

java
static class TrustAllManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}

Choix du fichier. Nous avons ajouté la classe TrustAllManager dans NetworkParser. Ce parser envoie déjà des documents à un serveur distant par le réseau. Un gestionnaire de connexions sécurisées y est donc réaliste.

Explication. En HTTPS, le programme vérifie le certificat du serveur pour être sûr de parler au bon site. Ici, les méthodes de vérification sont vides, donc tous les certificats sont acceptés. Un attaquant placé entre Tika et le serveur peut présenter un faux certificat, puis lire et modifier les données échangées. Les trois règles détectent chacune des trois méthodes, ce qui donne 9 findings. Le fichier contenait déjà 2 findings sur un socket non chiffré, qui ne sont pas comptés comme nouveaux.

2.6 Vulnérabilité 5, Permissions de fichier trop larges
Fichier. tika-core/src/main/java/org/apache/tika/io/TemporaryResources.java
Type. Permissions incorrectes sur une ressource
CWE. CWE-732
Règle Semgrep. gitlab.find_sec_bugs.OVERLY_PERMISSIVE_FILE_PERMISSION-1 et java.lang.security.audit.overly-permissive-file-permission
Ligne détectée. 215
Run. run #8, semgrep en échec et build non lancé

Code introduit.

java
public static void shareTempFile(Path path) throws IOException {
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxrwxrwx"));
}

Choix du fichier. Nous avons ajouté la méthode shareTempFile dans TemporaryResources. Cette classe crée déjà les fichiers temporaires de Tika pendant l'analyse des documents.

Explication. Les permissions rwxrwxrwx donnent le droit de lire, écrire et exécuter le fichier à tous les utilisateurs de la machine. Les fichiers temporaires de Tika contiennent le contenu des documents analysés. N'importe quel utilisateur pourrait donc les lire, les modifier ou y placer un programme. Le fichier contenait déjà 2 findings de path traversal, qui ne sont pas comptés comme nouveaux.

2.7 Vulnérabilité 6, Chiffrement faible DES en mode ECB
Fichier. tika-core/src/main/java/org/apache/tika/utils/StringUtils.java
Type. Utilisation d'un algorithme de chiffrement faible
CWE. CWE-327
Règle Semgrep. java.lang.security.audit.crypto.ecb-cipher, java.lang.security.audit.crypto.des-is-deprecated, gitlab.find_sec_bugs.DES_USAGE-1, gitlab.find_sec_bugs.ECB_MODE-1 et gitlab.find_sec_bugs.CIPHER_INTEGRITY-1
Ligne détectée. 233
Run. run #9, semgrep en échec et build non lancé

Code introduit.

java
public static byte[] obfuscate(byte[] data, byte[] key) throws Exception {
    Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "DES"));
    return cipher.doFinal(data);
}

Choix du fichier. Nous avons ajouté la méthode obfuscate dans StringUtils, la classe d'outils de Tika pour manipuler du texte. Une méthode qui masque des données y est réaliste.

Explication. DES utilise une clé de 56 bits, qui peut être retrouvée par force brute en peu de temps. Le mode ECB chiffre chaque bloc de la même façon, donc deux blocs identiques donnent le même résultat chiffré, ce qui laisse voir la structure des données. Ce mode ne vérifie pas non plus l'intégrité, donc un attaquant peut modifier le texte chiffré sans être détecté.

2.8 Vulnérabilité 7, Aléatoire prévisible
Fichier. tika-core/src/main/java/org/apache/tika/io/FilenameUtils.java
Type. Générateur de nombres aléatoires prévisible
CWE. CWE-330
Règle Semgrep. gitlab.find_sec_bugs.PREDICTABLE_RANDOM-1
Ligne détectée. 425 et 426
Run. run #10, semgrep en échec et build non lancé

Code introduit.

java
public static String randomFileName() {
    Random random = new Random();
    return "tika-" + Long.toHexString(random.nextLong());
}

Choix du fichier. Nous avons ajouté la méthode randomFileName dans FilenameUtils, la classe qui gère déjà les noms de fichiers dans Tika.

Explication. java.util.Random calcule chaque nombre à partir du précédent avec une formule fixe. Un attaquant qui observe quelques valeurs peut prédire les noms suivants. Il peut alors créer à l'avance un fichier piégé avec le même nom, que Tika utilisera ensuite. Il faudrait utiliser java.security.SecureRandom.

3. Comparaison des rapports Semgrep avant et après

Les deux rapports sont dans le dossier semgrep-rapports. Ils ont été produits avec la même commande sur tika-core.

Type de finding	Avant	Après	Nouveaux
Clé ou mot de passe codé en dur	31	31	0
Path traversal	2	2	0
XXE	2	2	0
Socket non chiffré	2	2	0
Injection de commande	0	1	1
Hachage faible MD5	0	2	2
Désérialisation non sécurisée	0	1	1
Validation SSL désactivée	0	9	9
Permissions de fichier trop larges	0	2	2
Chiffrement faible DES ECB	0	5	5
Aléatoire prévisible	0	2	2
Total	37	59	22

Les 37 findings d'origine sont toujours présents après nos modifications. Les 22 nouveaux findings correspondent exactement à nos 7 vulnérabilités. 

4. Rickroll

Nous avons écrit notre propre action composite réutilisable dans .github/actions/rickroll/action.yml. Elle n'a aucune dépendance externe et accepte un paramètre raison qui permet de l'utiliser dans d'autres jobs. Elle affiche une annotation d'erreur, un cadre dans le log et une miniature de la vidéo dans le résumé du run. Le message ressemble à un vrai rapport d'erreur, mais le lien de diagnostic mène à la vidéo de Rick Astley.

Elle est appelée à la fin du job build.

yaml
      - name: Rickroll si un test echoue
        if: failure()
        uses: ./.github/actions/rickroll
        with:
          raison: 'Un ou plusieurs tests de tika-core ont echoue'

La condition if failure() exécute l'étape seulement si une étape précédente du job a échoué. Elle apparaît donc quand un test de tika-core échoue, mais aussi si la compilation ou checkstyle échouent, car ces étapes font partie de la même commande Maven. Un échec de Semgrep ne déclenche pas le rickroll, car le job build ne démarre pas.

Test	Résultat	Lien
Échec de checkstyle, avant l'ajout du test	Build en échec, rickroll affiché	run #12
Ajout d'un test qui échoue volontairement	750 tests, 1 échec sur RickrollDemoTest.testEchecVolontaire, rickroll affiché	run #16
Retrait du test	Tous les tests passent, l'étape rickroll est sautée	run #17

## 5. Usage de l'intelligence artificielle

Nous avons utilisé Claude (Anthropic) comme assistant. Il nous a aidées à comprendre Semgrep et GitHub Actions
