package com.kurento.kmf.content.internal;

import java.io.IOException;
import java.util.concurrent.Future;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.kurento.kmf.content.PlayRequest;
import com.kurento.kmf.content.PlayerHandler;
import com.kurento.kmf.content.PlayerService;
import com.kurento.kmf.spring.KurentoApplicationContextUtils;

@WebServlet(asyncSupported = true)
public class PlayerHandlerServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger log = LoggerFactory
			.getLogger(PlayerHandlerServlet.class);

	@Autowired
	private PlayerHandler playerHandler;

	@Autowired
	private HandlerServletAsyncExecutor executor;

	private boolean useRedirectStrategy = true;

	@Override
	public void init() throws ServletException {
		super.init();

		// Recover application context associated to this servlet in this
		// context
		AnnotationConfigApplicationContext thisServletContext = KurentoApplicationContextUtils
				.getKurentoServletApplicationContext(this.getClass(),
						this.getServletName());

		// If there is not application context associated to this servlet,
		// create one
		if (thisServletContext == null) {
			// Locate the handler class associated to this servlet
			String handlerClass = this
					.getInitParameter(ContentApiWebApplicationInitializer.PLAYER_HANDLER_CLASS_PARAM_NAME);
			if (handlerClass == null || handlerClass.equals("")) {
				String message = "Cannot find handler class associated to handler servlet with name "
						+ this.getServletConfig().getServletName()
						+ " and class " + this.getClass().getName();
				log.error(message);
				throw new ServletException(message);
			}
			// Create application context for this servlet containing the
			// handler
			thisServletContext = KurentoApplicationContextUtils
					.createKurentoServletApplicationContext(this.getClass(),
							this.getServletName(), this.getServletContext(),
							handlerClass);

			try {
				PlayerService playerService = Class.forName(handlerClass)
						.getAnnotation(PlayerService.class);
				useRedirectStrategy = playerService.redirect();
			} catch (ClassNotFoundException e) {
				String message = "Cannot recover class " + handlerClass
						+ " on classpath";
				log.error(message);
				throw new ServletException(message);
			}
		}

		// Make this servlet to receive beans to resolve the @Autowired present
		// on it
		KurentoApplicationContextUtils
				.processInjectionBasedOnApplicationContext(this,
						thisServletContext);

	}

	@Override
	protected final void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		log.debug("Request received: " + req.getRequestURI());

		if (!req.isAsyncSupported()) {
			// Async context could not be created. It is not necessary to
			// complete it. Just send error message to
			resp.sendError(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"AsyncContext could not be started. The application should add \"asyncSupported = true\" in all "
							+ this.getClass().getName()
							+ " instances and in all filters in the associated chain");
			return;
		}
		if (playerHandler == null) {
			resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"Application must implement a PlayerHandler Spring bean");
			return;
		}

		String contentId = req.getPathInfo();
		if (contentId != null) {
			contentId = contentId.substring(1);
		}

		AsyncContext asyncCtx = req.startAsync();

		// Add listener for managing error conditions
		asyncCtx.addListener(new ContentAsyncListener());

		// PlayRequest playRequest = new PlayRequest(asyncCtx, contentId);
		PlayRequest playRequest = (PlayRequest) KurentoApplicationContextUtils
				.getBean("playRequest", asyncCtx, contentId,
						useRedirectStrategy);

		Future<?> future = executor.getExecutor().submit(
				new AsyncPlayerRequestProcessor(playerHandler, playRequest));

		// Store future for using it in case of error
		req.setAttribute(ContentAsyncListener.FUTURE_REQUEST_ATT_NAME, future);
	}
}
