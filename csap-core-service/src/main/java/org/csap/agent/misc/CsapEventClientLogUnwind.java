package org.csap.agent.misc;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

/**
 * Very simple class for unwinding CsapEvent client calls to source lines
 */
public final class CsapEventClientLogUnwind extends ExtendedLoggerWrapper {
    private static final long serialVersionUID = 1612276765616375L;
    private final ExtendedLoggerWrapper logger;

    private static final String FQCN = CsapEventClient.class.getName();

    private CsapEventClientLogUnwind(final Logger logger) {
        super((AbstractLogger) logger, logger.getName(), logger.getMessageFactory());
        this.logger = this;
    }

    @Override
    public void info(final String message, final Object... params) {
        logIfEnabled(FQCN, Level.INFO, null, message, params);
    }
    /**
     * Returns a custom Logger with the name of the calling class.
     * 
     * @return The custom Logger for the calling class.
     */
    public static CsapEventClientLogUnwind create() {
        final Logger wrapped = LogManager.getLogger();
        return new CsapEventClientLogUnwind(wrapped);
    }

}

