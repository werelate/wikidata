package org.werelate.parser;

import nu.xom.ParsingException;

import java.io.IOException;

public interface WikiPageParser {
   public void parse(String title, String text, int pageId, int latestRevId, String username, String timestamp, String comment) throws IOException, ParsingException;
}
