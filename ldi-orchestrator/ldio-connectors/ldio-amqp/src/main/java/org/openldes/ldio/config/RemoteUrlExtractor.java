package org.openldes.ldio.config;

import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openldes.ldio.config.AmqpConfig.*;

public class RemoteUrlExtractor {

    private final ComponentProperties config;

    public RemoteUrlExtractor(ComponentProperties config) {
        this.config = config;
    }

    String getRemoteUrl() {
        String remoteUrl = config.getProperty(REMOTE_URL);

        Pattern pattern = Pattern.compile(REMOTE_URL_REGEX, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(remoteUrl);
        boolean matchFound = matcher.find();
        if (matchFound) {
            return remoteUrl;
        } else {
            throw new IllegalArgumentException(REMOTE_URL_REGEX_ERROR);
        }
    }

}
