package com.oubeichen.oauth_test_server;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

public class Getusername extends HttpServlet {
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /*配置信息*/
    static final String DBINFO = "localhost:3306/joychat";
    static final String DBUSERNAME = "joychat";
    static final String DBPASSWD = "joychatpw";

    //private static final String VK_appID = "4654541";
    public static final String VK_API_URL = "https://api.vk.com/method/";
    public static final String VK_API_METHOD = "users.get";

    //private static final String OK_appID = "1110583040";
    private static final String OK_appKey = "CBAICDDDEBABABABA";
    private static final String OK_appSecret = "7A1D1FF34AA6AAE4240CF063";
    public static final String OK_API_URL = "http://api.odnoklassniki.ru/fb.do";
    public static final String OK_API_METHOD = "users.getCurrentUser";

    private CloseableHttpClient httpClient;

    /**
     * Constructor of the object.
     */
    public Getusername() {
        super();
        httpClient = HttpClientBuilder.create().build();
    }

    /**
     * Destruction of the servlet. <br>
     */
    public void destroy() {
        super.destroy(); // Just puts "destroy" string in log
        // Put your code here
    }

    public void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        String type = request.getParameter("authtype");
        String token = request.getParameter("token");
        String username = "";
        String uid = "";
        
        if(type == null) {
            out.println("You must specify an authtype!");
        } else if(token == null) {
            out.println("You must specify a token!");
        } else {
            if(type.equals("vk")){
                String secret = request.getParameter("secret");
                String content = "";
                if(secret != null) {// use nohttps method
                    URIBuilder uriBuilder = fromString(VK_API_URL + VK_API_METHOD);
                    uriBuilder.setParameter("access_token", token)
                              .setParameter("sig", generateVKSignature(VK_API_METHOD, token, secret));
                    HttpResponse respon = httpClient.execute(new HttpGet(uriBuilder.toString()));
                    content = EntityUtils.toString(respon.getEntity());
                } else {
                    // not implemented yet
                }
                try {
                    JSONObject jsonObject = new JSONObject(content);
                    uid = ((JSONArray)jsonObject.getJSONArray("response"))
                            .getJSONObject(0).getString("uid");
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    out.println("Error when getting user details from VK server, you must check your accesstoken");
                }
            } else if(type.equals("ok")){
                URIBuilder uriBuilder = fromString(OK_API_URL);
                uriBuilder
                        .setParameter("sig", generateOKSignature(OK_API_METHOD, token))
                        .setParameter("access_token", token)
                        .setParameter("application_key", OK_appKey)
                        .setParameter("method", OK_API_METHOD);
                HttpResponse respon = httpClient.execute(new HttpGet(uriBuilder.toString()));
                String content = EntityUtils.toString(respon.getEntity());
                try {
                    JSONObject jsonObject = new JSONObject(content);
                    System.out.println(content);
                    uid = jsonObject.getString("uid");
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    out.println("Error when getting user details from OK server, you must check your accesstoken");
                }
            } else {
                out.println("Only vk and ok are supported.");
            }
            
            if(!uid.equals("")) {
                username = getOrCreateUsername(type, uid);
                out.print(username);
            }
        }
        out.flush();
        out.close();
    }
    
    private URIBuilder fromString(String s) {
        try {
            return new URIBuilder(s);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    
    private synchronized String getOrCreateUsername(String type, String uid) {
        Connection conn = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql://" + DBINFO, DBUSERNAME, DBPASSWD);
            PreparedStatement stmt;
            String sql = "";
            String sql_insert = "";
            if(type.equals("vk")) {
                sql = "Select username from users where vk_id = ?";
                sql_insert = "Insert into users(username, vk_id) values(?, ?)";
            } else if(type.equals("ok")) {
                sql = "Select username from users where ok_id = ?";
                sql_insert = "Insert into users(username, ok_id) values(?, ?)";
            }
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, uid);
            ResultSet rs = stmt.executeQuery();
            if(rs.next()){ // there is a user matching this uid
                return rs.getString("username");
            } else { // need to create a new user
                // a simple way for generating a random number
                Boolean quit = false;
                String newname = "";
                String sql_check = "select * from users where username = ?"; 
                PreparedStatement stmt_check = conn.prepareStatement(sql_check);
                PreparedStatement stmt_insert = conn.prepareStatement(sql_insert);
                while(!quit){
                    String t = Long.valueOf(System.nanoTime()).toString();
                    t = t.substring(t.length()-6);
                    newname = type + "user_" + t;

                    stmt_check.setString(1, newname);
                    ResultSet rs_check = stmt.executeQuery();
                    if(rs_check.next()){ // there an user with the same username
                        continue;
                    }
                    stmt_insert.setString(1, newname);
                    stmt_insert.setString(2, uid);
                    if(stmt_insert.executeUpdate() == 1){ // one row changed
                        quit = true;
                    }
                }
                return newname;
            }
        } catch (ClassNotFoundException e) {
            // username never begin with a colon
            return ":error occurs when connecting mysql driver";
        } catch (SQLException e) {
            // username never begin with a colon
            return ":error occurs when executing sql queries";
        } finally {
            try {
                if(conn != null && !conn.isClosed()){
                    conn.close();
                }
            } catch (SQLException e) {
            }
        }
    }
    
    /**
     * The doGet method of the servlet. <br>
     *
     * This method is called when a form has its tag value method equals to get.
     * 
     * @param request the request send by the client to the server
     * @param response the response send by the server to the client
     * @throws ServletException if an error occurred
     * @throws IOException if an error occurred
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }

    /**
     * The doPost method of the servlet. <br>
     *
     * This method is called when a form has its tag value method equals to post.
     * 
     * @param request the request send by the client to the server
     * @param response the response send by the server to the client
     * @throws ServletException if an error occurred
     * @throws IOException if an error occurred
     */
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }

    /**
     * Initialization of the servlet. <br>
     *
     * @throws ServletException if an error occurs
     */
    public void init() throws ServletException {
        // Put your code here
    }

    private String generateVKSignature(String method, String accessToken, String secret) {
        StringBuilder params = new StringBuilder("/method/");
        params.append(method).append("?")
              .append("access_token=").append(accessToken)
              .append(secret);

        return DigestUtils.md5Hex(params.toString());
    }
    
    private String generateOKSignature(String method, String accessToken) {
        StringBuilder params = new StringBuilder();
        params
                .append("application_key=").append(OK_appKey)
                .append("method=").append(method)
                .append(DigestUtils.md5Hex(accessToken + OK_appSecret));

        return DigestUtils.md5Hex(params.toString());
    }
}
