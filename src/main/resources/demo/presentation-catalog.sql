INSERT INTO app.category (id, name, description) VALUES
('30000000-0000-0000-0000-000000000001', 'Cucina campana', 'Sapori di casa, tecniche tradizionali e ingredienti della cucina campana.'),
('c1000000-0000-4000-8000-000000000002', 'Panificazione', 'Impasti, pieghe, lievitazione e cottura di pizza e pane.'),
('c1000000-0000-4000-8000-000000000003', 'Pasticceria', 'Frolle, creme e dolci da forno, dalla pesatura alla finitura.'),
('c1000000-0000-4000-8000-000000000004', 'Cucina vegetariana', 'Verdure, cereali e legumi lavorati attraverso consistenze e cotture diverse.'),
('c1000000-0000-4000-8000-000000000005', 'Cucina mediterranea', 'Pesce, erbe aromatiche e condimenti essenziali della cucina di mare.'),
('c1000000-0000-4000-8000-000000000006', 'Pasta fresca', 'Sfoglia, ripieni e formatura della pasta preparata a mano.');

INSERT INTO app.ingredient (id, name, unit) VALUES
('a1000000-0000-4000-8000-000000000001', 'Patate', 'GRAM'),
('a1000000-0000-4000-8000-000000000002', 'Farina tipo 00', 'GRAM'),
('a1000000-0000-4000-8000-000000000003', 'Passata di pomodoro', 'GRAM'),
('a1000000-0000-4000-8000-000000000004', 'Fiordilatte', 'GRAM'),
('a1000000-0000-4000-8000-000000000005', 'Parmigiano grattugiato', 'GRAM'),
('a1000000-0000-4000-8000-000000000006', 'Basilico', 'GRAM'),
('a1000000-0000-4000-8000-000000000007', 'Olio extravergine di oliva', 'MILLILITER'),
('a1000000-0000-4000-8000-000000000008', 'Sale fino', 'GRAM'),
('a1000000-0000-4000-8000-000000000009', 'Pasta mista', 'GRAM'),
('a1000000-0000-4000-8000-000000000010', 'Provola affumicata', 'GRAM'),
('a1000000-0000-4000-8000-000000000011', 'Cipolla dorata', 'GRAM'),
('a1000000-0000-4000-8000-000000000012', 'Sedano', 'GRAM'),
('a1000000-0000-4000-8000-000000000013', 'Pomodorini', 'GRAM'),
('a1000000-0000-4000-8000-000000000014', 'Acqua', 'MILLILITER'),
('a1000000-0000-4000-8000-000000000015', 'Melanzane', 'GRAM'),
('a1000000-0000-4000-8000-000000000016', 'Lievito di birra fresco', 'GRAM'),
('a1000000-0000-4000-8000-000000000017', 'Rosmarino', 'GRAM'),
('a1000000-0000-4000-8000-000000000018', 'Burro', 'GRAM'),
('a1000000-0000-4000-8000-000000000019', 'Zucchero semolato', 'GRAM'),
('a1000000-0000-4000-8000-000000000020', 'Uovo sgusciato', 'GRAM'),
('a1000000-0000-4000-8000-000000000021', 'Succo di limone', 'MILLILITER'),
('a1000000-0000-4000-8000-000000000022', 'Scorza di limone grattugiata', 'GRAM'),
('a1000000-0000-4000-8000-000000000023', 'Cioccolato fondente', 'GRAM'),
('a1000000-0000-4000-8000-000000000024', 'Farina di mandorle', 'GRAM'),
('a1000000-0000-4000-8000-000000000025', 'Riso Carnaroli', 'GRAM'),
('a1000000-0000-4000-8000-000000000026', 'Funghi champignon', 'GRAM'),
('a1000000-0000-4000-8000-000000000027', 'Brodo vegetale', 'MILLILITER'),
('a1000000-0000-4000-8000-000000000028', 'Vino bianco', 'MILLILITER'),
('a1000000-0000-4000-8000-000000000029', 'Prezzemolo', 'GRAM'),
('a1000000-0000-4000-8000-000000000030', 'Spaghetti', 'GRAM'),
('a1000000-0000-4000-8000-000000000031', 'Vongole depurate con guscio', 'GRAM'),
('a1000000-0000-4000-8000-000000000032', 'Aglio', 'GRAM'),
('a1000000-0000-4000-8000-000000000033', 'Filetto di orata', 'GRAM'),
('a1000000-0000-4000-8000-000000000034', 'Zucchine', 'GRAM'),
('a1000000-0000-4000-8000-000000000035', 'Olive nere denocciolate', 'GRAM'),
('a1000000-0000-4000-8000-000000000036', 'Ceci cotti scolati', 'GRAM'),
('a1000000-0000-4000-8000-000000000037', 'Peperone rosso', 'GRAM'),
('a1000000-0000-4000-8000-000000000038', 'Cetriolo', 'GRAM'),
('a1000000-0000-4000-8000-000000000039', 'Ricotta vaccina scolata', 'GRAM'),
('a1000000-0000-4000-8000-000000000040', 'Spinaci freschi', 'GRAM'),
('a1000000-0000-4000-8000-000000000041', 'Salvia', 'GRAM');

INSERT INTO app.recipe (id, category_id, name, description, base_servings) VALUES
('50000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 'Gnocchi alla sorrentina', $recipe$Gnocchi di patate gratinati con pomodoro, fiordilatte e basilico. Tempo indicativo: 70 minuti. Dosi per una persona.
1. Lessare le patate con la buccia per circa 30 minuti, finché tenere. Pelarle, schiacciarle e far uscire il vapore per 5 minuti.
2. Incorporare la farina e 1 g di sale senza lavorare troppo. Formare filoncini, tagliare gli gnocchi e disporli su un vassoio infarinato usando poca farina prelevata dalla dose.
3. Cuocere la passata con tutto l'olio e il restante sale per 15 minuti. Spezzettare il basilico nel sugo. Scolare il fiordilatte e tagliarlo a cubetti.
4. Lessare gli gnocchi in acqua a leggero bollore e raccoglierli quando salgono in superficie. Condirli con il sugo e metà fiordilatte.
5. Trasferire in una piccola pirofila, distribuire il fiordilatte rimasto e il parmigiano. Gratinare a 220 °C per 10 minuti e lasciare riposare 3 minuti prima di servire.$recipe$, 1),
('b1000000-0000-4000-8000-000000000002', '30000000-0000-0000-0000-000000000001', 'Pasta e patate con provola', $recipe$Pasta mista cotta in casseruola con patate e provola filante. Tempo indicativo: 40 minuti. Dosi per una persona.
1. Pelare le patate e tagliarle a cubetti piccoli. Tritare cipolla e sedano, dividere i pomodorini e tagliare la provola a dadini.
2. Far appassire cipolla e sedano con tutto l'olio per 5 minuti a fuoco dolce. Aggiungere patate e pomodorini, mescolare per 2 minuti.
3. Versare 250 ml dell'acqua prevista, aggiungere il sale e cuocere coperto per 15 minuti. Scaldare separatamente i restanti 100 ml.
4. Aggiungere la pasta mista e cuocere per il tempo indicato sulla confezione, mescolando spesso. Versare gradualmente l'acqua calda tenuta da parte: il risultato deve essere cremoso.
5. Spegnere il fuoco e incorporare provola e parmigiano. Coprire per 2 minuti, mescolare e servire in un piatto fondo.$recipe$, 1),
('b1000000-0000-4000-8000-000000000003', '30000000-0000-0000-0000-000000000001', 'Parmigiana di melanzane', $recipe$Piccola parmigiana con melanzane arrostite, pomodoro e fiordilatte. Tempo indicativo: 60 minuti. Dosi per una persona.
1. Tagliare le melanzane a fette di circa 5 mm. Spennellarle con 10 ml di olio e disporle su carta da forno senza sovrapporle.
2. Cuocere a 210 °C per 20 minuti, girandole a metà cottura. Nel frattempo tagliare il fiordilatte e lasciarlo scolare.
3. Cuocere la passata con i restanti 5 ml di olio e il sale per 15 minuti. A fuoco spento unire il basilico spezzettato.
4. In una piccola pirofila alternare sugo, melanzane, fiordilatte e parmigiano. Terminare con sugo e formaggi, distribuendo tutti gli ingredienti tra gli strati.
5. Cuocere a 190 °C per 20 minuti. Lasciare riposare 10 minuti prima di portare in tavola, così gli strati si tagliano più facilmente.$recipe$, 1),
('50000000-0000-0000-0000-000000000001', 'c1000000-0000-4000-8000-000000000002', 'Pizza margherita', $recipe$Pizza individuale con impasto diretto, pomodoro, fiordilatte e basilico. Lavorazione: 25 minuti; lievitazione: circa 4 ore a 22–24 °C; cottura: 10–14 minuti.
1. Sciogliere il lievito nell'acqua e aggiungere la farina. Impastare per 5 minuti, quindi incorporare il sale e 3 ml di olio. Continuare per 5 minuti fino a ottenere un impasto omogeneo.
2. Coprire e lasciare lievitare per circa 3 ore, finché il volume è quasi raddoppiato. Formare un panetto e far riposare coperto per un'altra ora; i tempi dipendono dalla temperatura dell'ambiente.
3. Scolare il fiordilatte e tagliarlo a listarelle. Preriscaldare il forno statico e una teglia a 250 °C per almeno 30 minuti.
4. Stendere il panetto su carta da forno, spingendo dal centro verso il bordo. Distribuire la passata e i restanti 5 ml di olio.
5. Trasferire sulla teglia calda e cuocere per 7–9 minuti. Aggiungere il fiordilatte e terminare la cottura per altri 3–5 minuti, finché il fondo è dorato. Completare con il basilico.$recipe$, 1),
('b1000000-0000-4000-8000-000000000005', 'c1000000-0000-4000-8000-000000000002', 'Focaccia al rosmarino', $recipe$Focaccia individuale soffice, con superficie dorata e rosmarino. Lavorazione: 20 minuti; lievitazione: circa 3 ore a 22–24 °C; cottura: 20 minuti.
1. Sciogliere il lievito in 65 ml di acqua. Unire la farina e mescolare, poi incorporare 2 g di sale e 5 ml di olio. Lavorare per 5 minuti.
2. Lasciare riposare coperto per 20 minuti. Ripiegare i lembi verso il centro per due giri e far lievitare per circa 90 minuti, fino al raddoppio.
3. Ungere una piccola teglia con 5 ml di olio. Trasferire l'impasto, allargarlo delicatamente e farlo lievitare ancora 60 minuti.
4. Mescolare i restanti 10 ml di acqua con 5 ml di olio e 1 g di sale. Formare fossette con le dita, versare l'emulsione e distribuire gli aghi di rosmarino tritati.
5. Cuocere in forno statico già caldo a 220 °C per circa 20 minuti. Estrarre dalla teglia e far intiepidire su una griglia.$recipe$, 1),
('b1000000-0000-4000-8000-000000000006', 'c1000000-0000-4000-8000-000000000003', 'Crostata di limone', $recipe$Una crostatina di circa 10 cm con frolla e crema al limone. Lavorazione: 30 minuti; riposo: 30 minuti; cottura: circa 25 minuti, più raffreddamento.
1. Pesare l'uovo sgusciato dopo averlo mescolato con una forchetta. Lavorare 15 g di burro con 10 g di zucchero, incorporare 10 g di uovo e poi tutta la farina.
2. Compattare la frolla, avvolgerla e far riposare in frigorifero per 30 minuti. Stenderla tra due fogli di carta da forno e rivestire uno stampo da 10 cm.
3. Bucherellare il fondo, coprirlo con carta da forno e pesi da cottura. Cuocere a 175 °C per 15 minuti, togliere carta e pesi e proseguire per 5 minuti.
4. Mescolare in un pentolino i restanti 20 g di uovo, 15 g di zucchero, il succo e la scorza di limone. Cuocere a fuoco dolce mescolando continuamente fino a ottenere una crema densa; togliere dal fuoco e incorporare i restanti 5 g di burro.
5. Versare la crema nel guscio e lasciare raffreddare. Riporre in frigorifero per almeno un'ora prima di sformare e servire.$recipe$, 1),
('b1000000-0000-4000-8000-000000000007', 'c1000000-0000-4000-8000-000000000003', 'Torta caprese', $recipe$Tortino individuale al cioccolato e mandorle, con centro morbido e crosta sottile. Lavorazione: 20 minuti; cottura: 20–25 minuti, più raffreddamento.
1. Scaldare il forno statico a 170 °C. Rivestire uno stampo di circa 10 cm con carta da forno. Tritare il cioccolato e scioglierlo a bagnomaria insieme al burro, poi lasciare intiepidire.
2. Mescolare l'uovo sgusciato e pesarne la dose. Montarlo con lo zucchero per circa 5 minuti, fino a ottenere un composto chiaro e spumoso.
3. Incorporare il cioccolato tiepido con movimenti dal basso verso l'alto. Aggiungere la farina di mandorle in due volte, senza smontare il composto.
4. Versare nello stampo e cuocere per 20–25 minuti: la superficie deve essere assestata e l'interno ancora leggermente umido, senza impasto liquido.
5. Lasciare raffreddare completamente nello stampo prima di sformare. Servire senza ulteriori guarnizioni per valorizzare mandorle e cioccolato.$recipe$, 1),
('b1000000-0000-4000-8000-000000000008', 'c1000000-0000-4000-8000-000000000004', 'Risotto ai funghi', $recipe$Risotto mantecato con funghi rosolati e prezzemolo. Tempo indicativo: 35 minuti. Dosi per una persona.
1. Pulire e affettare i funghi, tritare la cipolla e il prezzemolo. Scaldare il brodo vegetale in un pentolino.
2. Rosolare i funghi con tutto l'olio per 7 minuti a fuoco medio alto. Aggiungere il sale e tenere da parte.
3. Far appassire la cipolla con 5 g di burro per 3 minuti. Unire il riso, tostarlo per 2 minuti e sfumare con il vino bianco.
4. Versare il brodo caldo poco alla volta e mescolare. Dopo 10 minuti aggiungere i funghi; continuare per altri 6–8 minuti fino alla cottura del riso, dosando il brodo per una consistenza fluida.
5. Spegnere e mantecare con i restanti 5 g di burro e il parmigiano. Aggiungere il prezzemolo, coprire per 2 minuti e servire.$recipe$, 1),
('b1000000-0000-4000-8000-000000000009', 'c1000000-0000-4000-8000-000000000005', 'Spaghetti alle vongole', $recipe$Spaghetti con vongole e fondo di cottura emulsionato. Tempo indicativo: 25 minuti, usando vongole già depurate. Dosi per una persona.
1. Sciacquare le vongole depurate e tritare il prezzemolo. Schiacciare l'aglio e farlo insaporire con tutto l'olio in una padella larga per 1 minuto.
2. Aggiungere le vongole e il vino bianco, coprire e cuocere per 4–5 minuti fino all'apertura. Togliere le vongole dalla padella ed eliminare quelle rimaste chiuse.
3. Filtrare il fondo con un colino a maglia fine e rimetterlo in padella. Sgusciare una parte delle vongole, conservandone alcune intere per il piatto.
4. Lessare gli spaghetti in acqua con il sale previsto e scolarli 2 minuti prima del tempo indicato. Tenere da parte una tazza dell'acqua di cottura.
5. Terminare la cottura degli spaghetti nel fondo filtrato, aggiungendo poca acqua di cottura se serve. Unire le vongole, saltare per 1 minuto e completare con il prezzemolo.$recipe$, 1),
('b1000000-0000-4000-8000-000000000010', 'c1000000-0000-4000-8000-000000000005', 'Orata al cartoccio', $recipe$Filetto di orata con zucchine, pomodorini e olive in un cartoccio individuale. Tempo indicativo: 35 minuti. Dosi per una persona.
1. Scaldare il forno statico a 190 °C. Tagliare le zucchine a rondelle sottili e i pomodorini a metà. Tritare il prezzemolo e affettare l'aglio.
2. Stendere un foglio ampio di carta da forno in una pirofila. Disporre le zucchine e adagiarvi il filetto di orata, dopo aver rimosso eventuali lische.
3. Aggiungere pomodorini, olive e aglio. Condire con tutto l'olio, il succo di limone, il sale e metà prezzemolo.
4. Chiudere il cartoccio ripiegando bene i bordi, lasciando spazio sopra il pesce. Cuocere per circa 20–25 minuti, secondo lo spessore del filetto, fino a quando la polpa risulta opaca e si separa facilmente.
5. Aprire il cartoccio nella pirofila, distribuire il prezzemolo rimasto e servire con il fondo di cottura e tutte le verdure.$recipe$, 1),
('b1000000-0000-4000-8000-000000000011', 'c1000000-0000-4000-8000-000000000004', 'Insalata di ceci e verdure', $recipe$Ceci con peperone arrostito, cetriolo e pomodorini, conditi al limone. Tempo indicativo: 30 minuti. Dosi per una persona.
1. Tagliare il peperone a falde, eliminando semi e filamenti. Condirlo con 3 ml di olio e arrostirlo a 220 °C per 15–20 minuti, girandolo a metà cottura.
2. Lasciarlo intiepidire e tagliarlo a strisce. Sciacquare e scolare bene i ceci già cotti; tagliare cetriolo e pomodorini a pezzi piccoli.
3. Tritare il prezzemolo e dividere le olive a metà. Riunire ceci, peperone, cetriolo, pomodorini e olive in una ciotola.
4. Emulsionare i restanti 7 ml di olio con il succo di limone e il sale. Versare sulle verdure e mescolare con il prezzemolo.
5. Lasciare insaporire per 10 minuti prima di servire, mescolando nuovamente per distribuire il condimento.$recipe$, 1),
('b1000000-0000-4000-8000-000000000012', 'c1000000-0000-4000-8000-000000000006', 'Ravioli ricotta e spinaci', $recipe$Ravioli fatti a mano con ripieno di ricotta e spinaci, conditi con burro e salvia. Lavorazione: 45 minuti; riposo della pasta: 30 minuti; cottura: 4–5 minuti. Dosi per una persona.
1. Mescolare l'uovo sgusciato, pesarne la dose e impastarlo con la farina, tenendone da parte 5 g per la stesura. Lavorare per 8 minuti, coprire e far riposare 30 minuti.
2. Lavare gli spinaci e farli appassire in una padella coperta per 4 minuti con la sola acqua rimasta sulle foglie. Scolarli, strizzarli bene e tritarli.
3. Mescolare spinaci, ricotta scolata, parmigiano e 1 g di sale. Stendere la pasta in due sfoglie sottili usando la farina tenuta da parte.
4. Distribuire il ripieno in piccoli mucchietti su una sfoglia, coprire con l'altra e premere intorno al ripieno per far uscire l'aria. Tagliare i ravioli e sigillare i bordi.
5. Lessare i ravioli per 4–5 minuti in acqua con i restanti 2 g di sale. Nel frattempo sciogliere il burro con la salvia a fuoco dolce. Scolare i ravioli e passarli delicatamente nel condimento con un cucchiaio della loro acqua di cottura.$recipe$, 1);

INSERT INTO app.recipe_ingredient (recipe_id, ingredient_id, quantity)
SELECT r.id, i.id, dose.quantity
FROM (VALUES
('Gnocchi alla sorrentina', 'Patate', 180),
('Gnocchi alla sorrentina', 'Farina tipo 00', 55),
('Gnocchi alla sorrentina', 'Passata di pomodoro', 120),
('Gnocchi alla sorrentina', 'Fiordilatte', 60),
('Gnocchi alla sorrentina', 'Parmigiano grattugiato', 10),
('Gnocchi alla sorrentina', 'Basilico', 2),
('Gnocchi alla sorrentina', 'Olio extravergine di oliva', 8),
('Gnocchi alla sorrentina', 'Sale fino', 2),
('Pasta e patate con provola', 'Pasta mista', 70),
('Pasta e patate con provola', 'Patate', 130),
('Pasta e patate con provola', 'Provola affumicata', 40),
('Pasta e patate con provola', 'Cipolla dorata', 25),
('Pasta e patate con provola', 'Sedano', 20),
('Pasta e patate con provola', 'Pomodorini', 40),
('Pasta e patate con provola', 'Olio extravergine di oliva', 8),
('Pasta e patate con provola', 'Acqua', 350),
('Pasta e patate con provola', 'Parmigiano grattugiato', 10),
('Pasta e patate con provola', 'Sale fino', 2),
('Parmigiana di melanzane', 'Melanzane', 250),
('Parmigiana di melanzane', 'Passata di pomodoro', 120),
('Parmigiana di melanzane', 'Fiordilatte', 70),
('Parmigiana di melanzane', 'Parmigiano grattugiato', 15),
('Parmigiana di melanzane', 'Basilico', 2),
('Parmigiana di melanzane', 'Olio extravergine di oliva', 15),
('Parmigiana di melanzane', 'Sale fino', 2),
('Pizza margherita', 'Farina tipo 00', 150),
('Pizza margherita', 'Acqua', 95),
('Pizza margherita', 'Lievito di birra fresco', 2),
('Pizza margherita', 'Sale fino', 3),
('Pizza margherita', 'Olio extravergine di oliva', 8),
('Pizza margherita', 'Passata di pomodoro', 80),
('Pizza margherita', 'Fiordilatte', 80),
('Pizza margherita', 'Basilico', 2),
('Focaccia al rosmarino', 'Farina tipo 00', 100),
('Focaccia al rosmarino', 'Acqua', 75),
('Focaccia al rosmarino', 'Lievito di birra fresco', 2),
('Focaccia al rosmarino', 'Sale fino', 3),
('Focaccia al rosmarino', 'Olio extravergine di oliva', 15),
('Focaccia al rosmarino', 'Rosmarino', 1),
('Crostata di limone', 'Farina tipo 00', 35),
('Crostata di limone', 'Burro', 20),
('Crostata di limone', 'Zucchero semolato', 25),
('Crostata di limone', 'Uovo sgusciato', 30),
('Crostata di limone', 'Succo di limone', 20),
('Crostata di limone', 'Scorza di limone grattugiata', 1),
('Torta caprese', 'Cioccolato fondente', 25),
('Torta caprese', 'Farina di mandorle', 25),
('Torta caprese', 'Burro', 20),
('Torta caprese', 'Zucchero semolato', 20),
('Torta caprese', 'Uovo sgusciato', 30),
('Risotto ai funghi', 'Riso Carnaroli', 80),
('Risotto ai funghi', 'Funghi champignon', 120),
('Risotto ai funghi', 'Cipolla dorata', 20),
('Risotto ai funghi', 'Brodo vegetale', 350),
('Risotto ai funghi', 'Vino bianco', 30),
('Risotto ai funghi', 'Burro', 10),
('Risotto ai funghi', 'Parmigiano grattugiato', 15),
('Risotto ai funghi', 'Olio extravergine di oliva', 5),
('Risotto ai funghi', 'Prezzemolo', 2),
('Risotto ai funghi', 'Sale fino', 1),
('Spaghetti alle vongole', 'Spaghetti', 90),
('Spaghetti alle vongole', 'Vongole depurate con guscio', 250),
('Spaghetti alle vongole', 'Olio extravergine di oliva', 10),
('Spaghetti alle vongole', 'Aglio', 3),
('Spaghetti alle vongole', 'Vino bianco', 30),
('Spaghetti alle vongole', 'Prezzemolo', 3),
('Spaghetti alle vongole', 'Sale fino', 3),
('Orata al cartoccio', 'Filetto di orata', 180),
('Orata al cartoccio', 'Zucchine', 100),
('Orata al cartoccio', 'Pomodorini', 80),
('Orata al cartoccio', 'Olive nere denocciolate', 20),
('Orata al cartoccio', 'Aglio', 2),
('Orata al cartoccio', 'Olio extravergine di oliva', 10),
('Orata al cartoccio', 'Succo di limone', 10),
('Orata al cartoccio', 'Prezzemolo', 2),
('Orata al cartoccio', 'Sale fino', 2),
('Insalata di ceci e verdure', 'Ceci cotti scolati', 150),
('Insalata di ceci e verdure', 'Peperone rosso', 80),
('Insalata di ceci e verdure', 'Cetriolo', 70),
('Insalata di ceci e verdure', 'Pomodorini', 80),
('Insalata di ceci e verdure', 'Olive nere denocciolate', 20),
('Insalata di ceci e verdure', 'Olio extravergine di oliva', 10),
('Insalata di ceci e verdure', 'Succo di limone', 15),
('Insalata di ceci e verdure', 'Prezzemolo', 3),
('Insalata di ceci e verdure', 'Sale fino', 2),
('Ravioli ricotta e spinaci', 'Farina tipo 00', 65),
('Ravioli ricotta e spinaci', 'Uovo sgusciato', 35),
('Ravioli ricotta e spinaci', 'Ricotta vaccina scolata', 60),
('Ravioli ricotta e spinaci', 'Spinaci freschi', 80),
('Ravioli ricotta e spinaci', 'Parmigiano grattugiato', 10),
('Ravioli ricotta e spinaci', 'Burro', 15),
('Ravioli ricotta e spinaci', 'Salvia', 1),
('Ravioli ricotta e spinaci', 'Sale fino', 3)
) AS dose(recipe_name, ingredient_name, quantity)
JOIN app.recipe r ON r.name = dose.recipe_name
JOIN app.ingredient i ON i.name = dose.ingredient_name;
