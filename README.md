# Tâche 2, améliorer les tests d'Apache Tika

Notre dossier dans le dépôt du cours s'appelle ALOUI_GARNA.

Répartition du travail entre nous deux : equivalent on a travailles les deux en meme temps sur le meme ordinateur.


## Ce que nous avons fait 

Nous avons travaillé sur le module tika-core d'Apache Tika. Nous avons choisi deux classes qui ont déjà des tests, mais dont plusieurs mutants ne sont pas détectés. Ce sont InputStreamDigester et CharsetUtils.

Nous avons installé ChatUniTest dans le fichier pom.xml, avec un modèle local qui tourne dans Ollama. Nous l'avons lancé sur les deux classes. Pour InputStreamDigester, aucun des 4 tests écrits par l'IA ne compile. Pour CharsetUtils, un fichier de 6 tests compile, mais 3 tests échouent parce que l'IA a deviné de mauvaises valeurs. Après 10 corrections à la main, ces tests passent, mais ils ne détectent aucun mutant de plus que les tests d'origine.

Nous avons donc écrit 12 tests à la main, 5 pour CharsetUtils et 7 pour InputStreamDigester. Le score de mutation passe de 64 % à 90 %. Il reste 4 mutants vivants.

## Notre environnement

| Élément | Valeur |
|---|---|
| Ordinateur | Windows 11, 7,7 Go de mémoire vive, carte NVIDIA GeForce MX450 de 2 Go |
| Java | 17, distribution Temurin |
| Maven | 3.10.0 |
| Apache Tika | 4.2.0-SNAPSHOT, module tika-core, branche tache2 |
| JaCoCo | 0.8.12 |
| pitest | 1.30.0 avec le plugin pitest-junit5-plugin 1.2.3 |
| ChatUniTest, plugin Maven | 2.1.1 |
| Ollama | 0.40.2, serveur local sur le port 11435 |
| Modèle de langage | qwen2.5-coder en version 3b |



## Étape 1

Nous avons d'abord mesuré la couverture de tika-core avec JaCoCo. Le module couvre 61 % de ses instructions. Nous avons chercher des classes petites, qui ont déjà un fichier de test et qui ne sont pas couvertes à 100 %.

| Classe | Fichier de test existant | Instructions couvertes | Branches couvertes | Lignes |
|---|---|---|---|---|
| InputStreamDigester | InputStreamDigesterTest | 87 % | 70 % | 35 |
| CharsetUtils | CharsetUtilsTest | 83 % | 78 % | 81 |

Nous avons ensuite lancé pitest avec les tests d'origine pour vérifier que ces classes ont bien des mutants vivants.

| Classe | Mutants | Tués | Score | Non détectés |
|---|---|---|---|---|
| InputStreamDigester | 17 | 7 | 41 % | 10 |
| CharsetUtils | 25 | 20 | 80 % | 5 |
| Total | 42 | 27 | 64 % | 15 |

Voici les lignes des mutants non détectés au départ.

| Classe | Mutants vivants | Mutants sans couverture |
|---|---|---|
| InputStreamDigester | lignes 68, 69, 75, 77, 122, 123, 134 et 137 | lignes 71 et 87 |
| CharsetUtils | aucun | lignes 107, 108, 118, 186 et 187 |


Les méthodes visées sont publiques, soit digest et digestSink pour InputStreamDigester, et isSupported, clean et forName pour CharsetUtils. Car ChatUniTest ignorer les méthodes privées et protégées.

Nous avons écarté plusieurs classes.

- Celles des packages embedder et quality, parce qu'elles sont à 0 % et n'ont aucun test.
- StringUtils, SystemUtils et ParserUtils, parce qu'elles n'ont pas de fichier de test à elles. Leur couverture vient de tests d'autres classes.
- ProcessUtils, parce que 7 de ses 8 tests sont ignorés sur Windows.
- XMLReaderUtils et TikaDates, parce qu'elles sont trop grosses pour un petit modèle sur processeur.

Nous avions d'abord choisi une troisième classe, DigestHelper. Nous l'avons retirée, parce que nous n'avons jamais lancé ChatUniTest dessus. Son fichier de test d'origine, DigestHelperTest, reste dans l'analyse, parce qu'il fait travailler une partie d'InputStreamDigester, la méthode digestSink.

## Étape 2

Dans le fichier pom.xml de tika-core, nous avons ajouté la dépendance chatunitest-starter en version 1.4.0 et le plugin chatunitest-maven-plugin en version 2.1.1. Le plugin pitest est dans le même fichier.

Le plugin est réglé pour parler à un modèle qui tourne sur notre ordinateur, avec l'adresse locale du serveur Ollama, le port 11435. Nous lui demandons 2 tests par méthode, avec au plus 3 tours de réparation, et sans plusieurs fils d'exécution, pour économiser la mémoire.

Le plugin accepte seulement quelques noms de modèles, dont code-llama, et refuse qwen2.5-coder 3b. Nous avons donc copié notre modèle sous le nom code-llama avec la commande ollama cp. C'est pour cela que le pom dit code-llama, alors que le modèle utilisé est qwen2.5-coder 3b.

## Étape 3

Nous avons lancé le plugin avec la commande mvn chatunitest class, une classe à la fois.

### InputStreamDigester

La génération a pris 40 minutes, de 1 h 44 à 2 h 24. Le plugin a écrit 4 tests, 2 pour digest et 2 pour digestSink. Pour chaque test, il essaie, puis il corrige jusqu'à 2 fois les erreurs de compilation. Aucun des 4 tests ne compile à la fin. Le tableau montre le nombre de lignes d'erreur du compilateur à chaque tour.

| Test | Tour 0 | Tour 1 | Tour 2 |
|---|---|---|---|
| digest, test 0 | 12 | 12 | 12 |
| digest, test 1 | 16 | 10 | 9 |
| digestSink, test 0 | 6 | 4 | 4 |
| digestSink, test 1 | 2 | 2 | 2 |

Les réparations ont aidé 2 tests sur 4, mais aucun n'est arrivé à zéro erreur.

### CharsetUtils

La génération a pris 38 minutes et 42 secondes, de 2 h 43 à 3 h 21.

| Méthode | Résultat |
|---|---|
| isSupported | 2 tests, aucun ne compile après 3 tours |
| clean | 6 tests qui compilent dès le premier tour, 3 réussissent et 3 échouent |
| forName | 2 tests, un échoue à l'exécution et l'autre ne compile pas |

### Où sont les tests générés?

Le plugin écrit ses fichiers dans un dossier chatunitest-tests de tika-core. Nous ne l'avons pas mis dans le dépôt, parce que ces fichiers cassent la construction. Le seul test généré que nous gardons est celui de la méthode clean, après corrections, dans le fichier CharsetUtilsGeneratedTest. Nous avons gardé de côté les messages d'erreur et l'historique des échanges avec le modèle.
Sur InputStreamDigester, aucun test ne compile. Sur CharsetUtils, seul le test de clean compile, mais il a quand même demandé 10 corrections.

## Étape 4

### Le test généré pour digest

Le premier test pour digest donne 12 lignes d'erreur. Voici ce que le modèle a mal fait.

- Il utilise des classes sans les importer, TemporaryResources, Date et Calendar.
- Il appelle une méthode seekTo qui n'existe pas dans TikaInputStream.
- Il appelle sept méthodes set sur TikaInputStream, alors qu'elles appartiennent à Metadata.
- Il appelle setLength, qui est privée.

Le test serait aussi mauvais s'il compilait. Il crée un faux TikaInputStream, puis le remplace par un vrai, ce qui annule les réglages du faux. L'encodeur est un faux lui aussi, donc aucun digest n'est comparé à une vraie valeur. Les vérifications regardent si des méthodes ont été appelées, jamais ce qu'elles ont produit.

### Les tests générés pour clean

Le fichier compile, mais 3 tests sur 6 échouent.

| Test | Ce que l'IA attendait | Ce que le code retourne | Cause |
|---|---|---|---|
| testCleanWithISOCharset | iso-8859-1 | ISO-8859-1 | l'IA a deviné la casse |
| testCleanWithCPCharset | cp1252 | windows-1252 | elle a mal compris l'alias cp |
| testCleanWithICUCharset | icu-charset | null | elle a inventé un nom |

Les 3 tests qui réussissent refont ce que CharsetUtilsTest vérifie déjà. Le fichier contient aussi du code inutile, comme des champs privés recopiés de la classe testée, une méthode de préparation vide et des imports jamais utilisés.

### Les 10 corrections à la main

1. Valeur attendue du test ISO, de iso-8859-1 à ISO-8859-1.
2. Valeur attendue du test CP, de cp1252 à windows-1252.
3. Test ICU, valeur attendue null, et test renommé testCleanWithUnknownCharsetName.
4. Suppression du code inutile.
5. Suppression des imports inutilisés.
6. Nettoyage de la déclaration de la classe et nouveau nom, CharsetUtilsGeneratedTest.
7. Ajout de l'en-tête de licence, que les contrôles Rat et Checkstyle exigent.
8. Fichier en UTF-8 sans BOM, ce que Checkstyle exige.
9. Fins de ligne de type LF au lieu de CRLF.
10. Retour à la ligne à la fin du fichier.


### Les oracles de l'IA contre les nôtres

Les tests de l'IA pour digest sont peu pertinents et peu précis, parce qu'ils vérifient des appels et non des résultats. Ceux pour clean sont plus utiles, mais ils répètent ce qui existe déjà.
Les oracles de l'IA sont des suppositions. Trois sur six sont fausses pour clean. Quand nous les avons corrigés, nous avons simplement copié la valeur que le code retourne.
Nos tests écrits à la main utilisent des valeurs calculées autrement, comme le digest que calcule MessageDigest dans le JDK, la longueur connue de 11 octets du texte hello world, ou ce que la documentation de la méthode promet. Ce sont de meilleurs oracles.

## Étape 5, mesurer avec pitest

Nous avons ajouté pitest au fichier pom.xml, avec le plugin pitest-junit5-plugin, parce que Tika utilise JUnit 5.

| Étape | Mutants tués sur 42 | Score | Sans couverture | Lignes couvertes |
|---|---|---|---|---|
| Tests d'origine | 27 | 64 % | 7 | 94 sur 116 |
| Plus les tests générés et corrigés pour clean | 27 | 64 % | 7 | 94 sur 116 |
| Plus CharsetUtilsIcuTest | 32 | 76 % | 2 | 100 sur 116 |
| Plus InputStreamDigesterManualTest | 38 | 90 % | 0 | 102 sur 116 |

Les trois premières lignes viennent de nos mesures faites quand DigestHelper faisait encore partie de l'analyse, en retirant ses résultats. La dernière ligne est une mesure directe avec les deux classes.

Le score final de chaque classe est de 13 sur 17 pour InputStreamDigester et de 25 sur 25 pour CharsetUtils. La part des mutants atteints qui sont tués est de 90 %.


## Étape 6, pourquoi les tests de l'IA n'ajoutent rien

Après l'ajout des tests générés pour clean, le score reste à 27 sur 42. Ces tests appellent clean avec des noms courants, comme UTF-8, cp-1252 et windows-1252, que CharsetUtilsTest vérifie déjà.

Les 5 mutants de CharsetUtils qui échappaient aux tests sont tous dans un morceau de code qui utilise ICU4J, une bibliothèque optionnelle. Ce code ne s'exécute que si ICU4J est présente, et ce n'est pas le cas dans tika-core. Aucun nom de charset normal ne peut donc l'atteindre. Le test ICU de l'IA le visait sans le savoir, et c'est pour ça qu'il échouait.

## Étape 7, nos tests écrits à la main

Les 12 tests sont dans tika-core, dossier src test java, packages org.apache.tika.utils et org.apache.tika.digest.

### CharsetUtilsIcuTest, 5 tests

CharsetUtils garde deux champs statiques, getCharsetICU et isSupportedICU, qui restent vides sans ICU4J. Dans le test, nous les remplaçons, avec la réflexion de Java, par deux méthodes d'une fausse classe FakeIcu que nous avons écrite. Après chaque test, nous remettons les anciennes valeurs. Ainsi nous exécutons le code ICU sans ajouter de bibliothèque.

| Test | Ce qu'il vérifie | Pourquoi ces données | Comment on sait la bonne réponse |
|---|---|---|---|
| testIsSupportedTrustsIcuWhenItAcceptsTheName | si ICU accepte un nom, isSupported répond vrai | un nom icu-only-charset que le JDK ne connaît pas, et un faux ICU qui répond vrai | le JDK refuserait ce nom, donc seul le chemin ICU peut répondre vrai |
| testIsSupportedFallsBackToJdkWhenIcuRejectsTheName | si ICU refuse, la méthode demande au JDK | le même nom inconnu, puis UTF-8 | faux pour le nom inconnu, vrai pour UTF-8 que le JDK connaît |
| testIsSupportedReturnsFalseWhenIcuThrows | une erreur d'ICU donne faux | un faux ICU qui lance IllegalStateException | la méthode ne doit jamais lancer d'exception |
| testForNameUsesIcuCharsetWhenJdkDoesNotKnowTheName | forName utilise le charset donné par ICU | un nom inconnu du JDK et un faux ICU qui retourne UTF-16LE | le résultat doit être UTF-16LE |
| testCleanReturnsTheNameOfTheIcuCharset | clean retourne le nom du charset d'ICU | le même nom inconnu | le nom officiel de UTF-16LE, c'est UTF-16LE |

### InputStreamDigesterManualTest, 7 tests

Pour tous ces tests, nous utilisons le texte hello world, qui fait 11 octets, l'algorithme SHA-256 et un encodeur en Base64. Le digest attendu est calculé par MessageDigest du JDK, sans passer par notre classe.

| Test | Ce qu'il vérifie | Pourquoi ces données | Comment on sait la bonne réponse |
|---|---|---|---|
| testDigestStoresTheContentLength | la longueur et le digest sont enregistrés | un texte dont la longueur est connue | longueur de 11, digest égal à celui du JDK |
| testDigestKeepsAnExistingContentLength | une longueur déjà présente n'est pas écrasée | des métadonnées avec la longueur 999 avant l'appel | elle reste 999 |
| testDigestAcceptsANullParseContext | un contexte vide est accepté | un contexte null | pas d'exception et bon digest |
| testStreamCanBeReadAgainAfterTheDigest | on peut relire le flux après le digest | le même texte | les octets relus sont ceux d'origine |
| testKeyOutsideTheDigestNamespaceIsStoredUnderItsOwnName | une clé hors de l'espace digest de Tika est acceptée | une clé de la forme custom digest sha256 | le digest est rangé sous la clé donnée |
| testDigestUsesTheProviderOfASubclass | une sous-classe peut choisir son fournisseur | une sous-classe qui retourne le fournisseur SUN | le digest égale celui du JDK |
| testDigestPropertyIsRegisteredForBothKeyShapes | les deux formes de clé donnent une propriété enregistrée | une clé de l'espace digest et une clé hors de cet espace | Property get ne retourne pas null et le nom égale la clé |

Le septième test passe, mais il ne détecte aucun mutant de plus.

## Quels mutants nos tests détectent

| Classe | Ligne | Mutant | Pourquoi nos tests le détectent |
|---|---|---|---|
| CharsetUtils | 107 et 108 | une condition inversée et un retour vrai | avec un faux ICU qui accepte un nom inconnu du JDK, la méthode doit répondre vrai |
| CharsetUtils | 118 | un retour vrai dans le cas d'une erreur | quand ICU lance une exception, la méthode doit répondre faux |
| CharsetUtils | 186 et 187 | une condition inversée et un retour null | quand ICU donne un charset, forName doit le retourner |
| InputStreamDigester | 75 | condition inversée sur la longueur déjà présente | la valeur 999 serait écrasée par 11 |
| InputStreamDigester | 77 et 137 | un appel à Metadata set retiré et un appel à setContentLength retiré | la longueur resterait absente au lieu de valoir 11 |
| InputStreamDigester | 87 | un retour null du chemin avec fournisseur | le digest doit être le même que celui du JDK |
| InputStreamDigester | 122 | condition inversée sur le contexte null | le code appellerait une méthode sur null et lancerait une exception |
| InputStreamDigester | 134 | une addition changée en soustraction | la longueur serait moins 11 au lieu de 11 |

Nous avons associé chaque mutant à son test en lisant le code et en comparant les rapports de pitest avant et après chaque ajout.

## Mutants qui restent

Il reste 4 mutants vivants, tous dans InputStreamDigester.

| Lignes | Mutant | Notre explication |
|---|---|---|
| 68, 69 et 71 | une condition inversée et deux retours null dans mintProperty | la propriété est rangée dans une table et dans un champ qui sert seulement à nommer la clé des métadonnées, donc le résultat ne se voit pas de l'extérieur |
| 123 | l'appel à enableRewind retiré | tryRetainInMemory et rewind suffisent pour relire un flux fait à partir d'un tableau d'octets |



## Étape 8, exécution dans la GitHub Action

nous avons écrit le fichier tache2.yml. Il se lance à chaque envoi sur la branche tache2. Il construit tika-core, exécute nos 18 tests ajoutés, puis relance pitest. 
Le dernier run, s'est terminé avec succès en 1 minute et 26 secondes. La construction a pris 47 secondes, les tests 13 secondes et pitest 13 secondes. Les 18 tests ajoutés passent, soit 7 pour InputStreamDigesterManualTest, 6 pour CharsetUtilsGeneratedTest et 5 pour CharsetUtilsIcuTest. Il n'y a aucun échec et aucun test ignoré. Checkstyle ne signale aucune violation. Pitest examine 6 classes de test et génère 42 mutants, dont 38 sont tués, soit 90 %. Il n'y a aucun mutant sans couverture. Ces chiffres sont identiques à ceux de notre ordinateur sous Windows.

## Usage de l'intelligence artificielle

Nous avons utilisé l'IA de deux façons.

- ChatUniTest, avec le modèle qwen2.5-coder 3b qui tourne en local dans Ollama, pour générer des tests.
- Claude, un assistant d'Anthropic, Il nous a guidés dans l'installation des outils, aidés à lire et comprendre les rapports de JaCoCo et de pitest, et a propose plusieurs versions du code des tests écrits à la main, il a aussi répondu à nos questions pour nous aider à comprendre chaque étape.

Nous avons lancé nous-mêmes toutes les commandes, exécuté tous les tests et lu tous les rapports sur notre ordinateur.



