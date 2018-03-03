/*
 * Mercury-SSH
 * Copyright (C) 2018 Skarafaz
 *
 * This file is part of Mercury-SSH.
 *
 * Mercury-SSH is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Mercury-SSH is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Mercury-SSH.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.skarafaz.mercury.misc;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public abstract class ExponentialBackoff<Params, Result> {
    private static final Logger logger = LoggerFactory.getLogger(ExponentialBackoff.class);
    private static final int MAX_BACKOFF = 32000;

    public Result execute(int attempts, Params... params) {
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return doWork(attempt, params);
            } catch (Exception exception) {
                boolean keepOn = handleAttemptFailure(attempt, exception, params);

                if (keepOn) {
                    doWait(attempt, attempts);
                } else {
                    break;
                }
            }
        }
        return handleFailure(params);
    }

    protected abstract Result doWork(int attempt, Params... params) throws Exception;

    protected boolean handleAttemptFailure(int attempt, Exception exception, Params... params) {
        return true;
    }

    protected Result handleFailure(Params... params) {
        return null;
    }

    private void doWait(int attempt, int attempts) {
        long time = attempt < attempts ? (long) Math.min(Math.pow(2, attempt - 1) + new Random().nextInt(1000 + 1), MAX_BACKOFF) : 0;

        logger.debug("backoff time: {}", time);

        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
