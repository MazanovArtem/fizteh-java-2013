package ru.fizteh.fivt.students.dzvonarev.filemap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ServletRollback extends HttpServlet {

    private TransactionManager manager;

    public ServletRollback(TransactionManager transactionManger) {
        manager = transactionManger;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String transactionId = request.getParameter("tid");
        if (transactionId == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "transaction id not found");
            return;
        }
        if (!Transaction.isValid(transactionId)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid transaction id");
        }
        Transaction transaction = manager.getTransaction(transactionId);
        if (transaction == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "no transaction is found");
            return;
        } // run transaction
        int cntOfChanges = transaction.rollback();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF8");
        response.getWriter().println(String.format("diff=" + cntOfChanges));
    }

}
