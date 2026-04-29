package com.smartcampus.filter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Redirects any request to the root context path (/) to /api/v1
 * so visiting localhost:8080/smart-campus-api/ works correctly.
 */
public class RootRedirectServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String contextPath = req.getContextPath();
        resp.sendRedirect(contextPath + "/api/v1");
    }
}
