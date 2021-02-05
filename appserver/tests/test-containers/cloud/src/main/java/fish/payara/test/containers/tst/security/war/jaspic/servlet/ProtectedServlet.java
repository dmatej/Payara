package fish.payara.test.containers.tst.security.war.jaspic.servlet;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Arjan Tijms
 */
@WebServlet(urlPatterns = "/protected/servlet")
public class ProtectedServlet extends HttpServlet {
    public static final String RESPONSE_PROTECTED_SERVLET_INVOKED = "Protected resource invoked\n";
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ProtectedServlet.class.getName());


    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        LOG.info("doGet(request, response)");
        response.getWriter().write(RESPONSE_PROTECTED_SERVLET_INVOKED);

        if (request.getParameter("doLogout") != null) {
            request.logout();
        }
    }

}
