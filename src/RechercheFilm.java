import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.*;
import java.util.*;

/**
 * Recherche de films dans une base de données
 * A partir d'une requête simplifiée
 */
public class RechercheFilm
{
    private Connection conn = null;
    private Map<Integer, InfoFilm2> infoFilmsMap = new LinkedHashMap<>();
    private List<String> arguments = new ArrayList<>();

    /**
     * Constructeur : ouvre la base de données
     * @param monFichierSQLite nom du fichier bdd
     */
    public RechercheFilm(String monFichierSQLite)
    {
        try
        {
            String url = "jdbc:sqlite:"+monFichierSQLite;
            conn = DriverManager.getConnection(url);
        } catch (SQLException e)
        {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Ferme la base de données
     * Pour sortir proprement.
     */
    public void fermeBase()
    {
        try {
            if (conn != null)
                conn.close();
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }

    /**
     * @param requete Requête dans un langage simplifié
     *                mots-clés : TITRE|DE|AVEC|PAYS|EN|AVANT|APRES
     * @return  une chaîne de caractère sous format json avec les informations des films sélectionnés
     * Le résultat est limité à 100 films, triés par ordre décroissant d'année de sortie, puis par ordre alphabétique
     */
    public String retrouve(String requete)
    {
        requete = versSqlFinal(requete);
        if(requete.substring(0,6).equals("fail :"))
            return "{\"erreur\":\""+requete+"\"}";
        try {
            fetchData(requete);
        }
        catch (SQLException e) {
            e.printStackTrace();
            return "";
        }
        StringBuilder retour = new StringBuilder("{\"resultat\":[");
        List<InfoFilm2> infoFilm2List = new ArrayList<>();

        for(int s : infoFilmsMap.keySet())
            infoFilm2List.add(infoFilmsMap.get(s));
        int size = infoFilm2List.size();
        for(int i = 0; i < Math.min(size, 100); i++)
        {
            retour.append(infoFilm2List.get(i));
            retour.append(",");
        }
        retour = new StringBuilder(size == 0 ? retour.toString() : retour.substring(0, retour.length() - 1));
        retour.append("]");
        if(size > 100)
            retour.append(",\"info\":\"Résultat limité à 100 films\"");
        retour.append("}");
        return retour.toString();
    }
    private void fetchData(String requete) throws SQLException
    {
        ResultSet resultSet;
        String prenom, nom;
        PreparedStatement preparedStatement =
                conn.prepareStatement(requete);
        for(int i = 1; i <= arguments.size(); i++)
            preparedStatement.setString(i,arguments.get(i-1));
        resultSet = preparedStatement.executeQuery();
        while(resultSet.next())
        {
            prenom = null;
            nom = null;
            if(resultSet.getString("role") != null)
            {
                if( resultSet.getString("role").equals("R"))
                {
                    prenom = resultSet.getString("prenom") ;
                    nom = resultSet.getString("nom");
                }
                else if(resultSet.getString("role").equals("A"))
                {
                    prenom = resultSet.getString("prenom");
                    nom = resultSet.getString("nom");
                }
            }
            addInfoFilm(resultSet.getString("titre"), prenom, nom, resultSet.getString("role"), resultSet.getString("nomPays"), resultSet.getInt("annee"),
                    resultSet.getInt("duree"), resultSet.getString("autres_titress"), resultSet.getInt("id_film"));
        }
    }
    private void addInfoFilm(String _titre, String prenom, String nom, String role, String _pays, int _annee, int _duree, String _autres_titres, int _id)
    {
        ArrayList<NomPersonne> acteurs = new ArrayList<>(), realisateurs = new ArrayList<>();
        ArrayList<String> autres_titres = new ArrayList<>();
        TreeSet<String> setAutresTitres = new TreeSet<>();
        if(role != null)
            if(role.equals("A"))
                acteurs.add(new NomPersonne(nom, prenom));
            else
                realisateurs.add(new NomPersonne(nom, prenom));
        if(_autres_titres != null)
        {
            Collections.addAll(setAutresTitres, _autres_titres.split("[|]"));
            autres_titres.addAll(setAutresTitres);
        }
        if(infoFilmsMap.get(_id) == null)    // il n'y a pas encore ce film dans la map, je le rajoute
        {
            InfoFilm2 infoFilm = new InfoFilm2(_titre, realisateurs, acteurs, _pays, _annee, _duree, autres_titres);
            infoFilmsMap.put(_id, infoFilm);
        }
        else // ce film est présent dans la map, il faut donc ajouter des réalisateurs ou acteurs
        {
            ArrayList<NomPersonne> hold;
            if(role != null)
            {
                if(role.equals("A"))
                {
                    hold = new ArrayList<>(infoFilmsMap.get(_id).get_acteurs());
                    hold.add(acteurs.get(0));
                    infoFilmsMap.put(_id, new InfoFilm2(_titre, infoFilmsMap.get(_id).get_realisateurs() , hold, _pays, _annee, _duree, autres_titres));
                }
                else
                {
                    hold = new ArrayList<>(infoFilmsMap.get(_id).get_realisateurs());
                    hold.add(realisateurs.get(0));
                    infoFilmsMap.put(_id, new InfoFilm2(_titre, hold, infoFilmsMap.get(_id).get_acteurs(), _pays, _annee, _duree, autres_titres));
                }
            }

        }
    }
    private String versSqlFinal(String simplifiedRequest)
    {
        simplifiedRequest = simplifiedRequest.toLowerCase().replaceAll("è", "e").toUpperCase();
        String builtQuery = simpleRequestToSQL(simplifiedRequest).replaceAll(" +", " ");
        if(builtQuery.substring(0,6).equals("fail :"))
            return builtQuery;
        return  "with filtre as (" + builtQuery + " )" +
                " SELECT " +
                " films.id_film, films.titre, films.annee, films.duree,  pays.nom AS nomPays, group_concat(autres_titres.titre, '|') AS autres_titress, " +
                " personnes.prenom, personnes.nom, generique.role " +
                " FROM filtre " +
                " JOIN films on films.id_film = filtre.id_film  LEFT JOIN autres_titres on autres_titres.id_film = films.id_film " +
                " JOIN pays on pays.code = films.pays  LEFT JOIN generique on generique.id_film = films.id_film " +
                " LEFT JOIN personnes on personnes.id_personne = generique.id_personne GROUP BY films.id_film, films.titre, films.annee, films.duree, " +
                " nomPays, personnes.prenom, personnes.nom,  generique.role ORDER BY films.annee DESC, films.titre ASC";
    }
    private String getConditionTitre(String titre)   // filtre pour avoir ids des films qui match le titre
    {
        arguments.add(titre);
        return " select id_film from recherche_titre where titre match ? ";
    }
    private String getConditionPays(String pays)
    {
        arguments.add(pays);
        arguments.add(pays);
        return  " SELECT films.id_film from films join pays on films.pays = pays.code WHERE films.pays like ? or pays.nom like ? ";
    }
    private String getConditionAnnee(String annee, String operator)
    {
        try
        {
            Integer.parseInt(annee);
        }
        catch (Exception e)
        {
            return "fail : les mots clés APRES, AVANT et EN doivent être suivi d'un nombre entier (l'année)";
        }
        arguments.add(annee);
        return " SELECT films.id_film from films  WHERE films.annee " + operator + " ? ";
    }
    private String getConditionNomPrenom(String prenom, String nom, String role)
    {
        arguments.add(role);
        arguments.add(prenom.replaceAll("^MC", "MAC"));
        arguments.add(prenom.replaceAll("^MC", "MAC"));
        arguments.add(nom);
        arguments.add(nom);
        arguments.add(role);
        arguments.add(nom.replaceAll("^MC", "MAC"));
        arguments.add(nom.replaceAll("^MC", "MAC"));
        arguments.add(prenom);
        arguments.add(prenom);
        return "SELECT id_film from generique join personnes on generique.id_personne = personnes.id_personne " +
                "where generique.role = ?  and (personnes.nom like ?  or personnes.nom_sans_accent like ? )  and (personnes.prenom like ? || '%' or personnes.prenom_sans_accent like ? || '%' ) " +
                " union " +
                "SELECT id_film from generique join personnes on generique.id_personne = personnes.id_personne " +
                "where generique.role = ?  and (personnes.nom like ? or personnes.nom_sans_accent like ? )  and (personnes.prenom like ? || '%' or personnes.prenom_sans_accent like ? || '%' ) ";
    }
    private String getConditionPersonnes(String name, String role)  // test de toutes les possibilités de combinaison + inversions nom/prenom
    {
        name = name.trim().replaceAll(" +", " ");
        String[] split = name.split(" ");
        StringBuilder res = new StringBuilder();
        res.append("select id_film from (");
        StringBuilder prenom = new StringBuilder();
        StringBuilder nom = new StringBuilder();
        for(int max = -1; max < split.length-1; max++)  // en commençant à -1, s'il y a un seul terme, il est considéré comme Nom
        {
            for(int i = 0; i <= max; i++)
                prenom.append(split[i]).append(" ");

            for(int i = max + 1; i < split.length; i++)
                nom.append(split[i]).append(" ");

            res.append(getConditionNomPrenom(prenom.toString().trim(), nom.toString().trim(), role));
            if(max != split.length - 2)
                res.append(" union ");
            prenom = new StringBuilder();
            nom = new StringBuilder();
        }
        res.append(")");
        return res.toString();
    }
    private String switchOnKeyword(String keyword, String condition)
    {
        condition = condition.trim();
        switch (keyword)
        {
            case "TITRE" :
                return getConditionTitre(condition);
            case "PAYS" :
                return getConditionPays(condition);
            case "EN" :
                return getConditionAnnee(condition,"=");
            case "AVANT" :
                return getConditionAnnee(condition, "<");
            case "APRES" :
                return getConditionAnnee(condition, ">");
            case "DE" :
                return getConditionPersonnes(condition, "R");
            case "AVEC" :
                return getConditionPersonnes(condition, "A");
        }
        return "fail : ... somehow";
    }
    private String simpleRequestToSQL(String request)   // parsing ici
    {
        String keyWords = "|TITRE|DE|AVEC|PAYS|EN|AVANT|APRES|";
        StringBuilder finalS = new StringBuilder();
        StringBuilder wordTmp;
        String[] splitOnAnd = request.split(",");
        String motClePrec = "";
        for(int i = 0; i < splitOnAnd.length; i++)
        {
            String splitOnAndTmp = splitOnAnd[i].trim(); // exemple [TITRE A OU TITRE B]
            String[] splitOnOu = splitOnAndTmp.split(" OU ");
            if(splitOnOu.length >= 2) // => il y a au moins un ou
                finalS.append(" select id_film from (\n");
            for(int j = 0; j < splitOnOu.length; j++)
            {
                String subOu = splitOnOu[j].trim();    // exemple [TITRE A]
                wordTmp = new StringBuilder();
                int nbrEspaces = 0;
                for(int k = 0; k < subOu.length(); k++)
                {
                    char c = subOu.charAt(k);
                    if(c == ' ' && nbrEspaces == 0) // Quand on arrive sur le premier espace
                    {
                        nbrEspaces++;
                        if(keyWords.contains("|"+wordTmp+"|") && !wordTmp.toString().equals("TITRE|DE|AVEC|PAYS|EN|AVANT|APRES"))
                        {
                            motClePrec = wordTmp.toString(); // save le keyword au cas où l'utilisateur ne réécrit pas (ex TITRE A OU B)
                            wordTmp = new StringBuilder();
                        }
                        else
                        {
                            wordTmp.append(c);
                            if(motClePrec.isEmpty()) // si le premier mot n'est pas un keyword et qu'il n'y a pas de motClePrec, fail
                                return "fail : pas de mot keyword détecté : (TITRE|DE|AVEC|PAYS|EN|AVANT|APRES)";
                        }
                    }
                    else
                        wordTmp.append(c);
                    if(k == subOu.length() - 1) // j'arrive à la fin du [TITRE A]
                        if(!motClePrec.isEmpty())
                            finalS.append(switchOnKeyword(motClePrec, wordTmp.toString()));
                        else
                            return (!keyWords.contains("|"+wordTmp+"|") || !wordTmp.toString().equals("TITRE|DE|AVEC|PAYS|EN|AVANT|APRES"))? "fail : pas de mot keyword détecté : (TITRE|DE|AVEC|PAYS|EN|AVANT|APRES)" : "fail : keyword trouvé mais pas de condition";
                }
                if(j + 1 != splitOnOu.length) // il reste encore des ou
                    finalS.append(" union ");
            }
            if(splitOnOu.length >= 2) // => il y avait au moins un ou
                finalS.append(" )\n");
            if(i != splitOnAnd.length -1)
                finalS.append(" intersect ");
        }
        return finalS.toString();
    }
}