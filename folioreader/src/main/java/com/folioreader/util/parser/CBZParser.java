package com.folioreader.util.parser;


import org.readium.r2.shared.Link;
import org.readium.r2.streamer.container.CbzContainer;
import org.readium.r2.shared.Publication;

/**
 * Class to handle parsing of the .cbz (Comic book archive)
 * ref => https://en.wikipedia.org/wiki/Comic_book_archive
 *
 * @author gautam chibde on 5/6/17.
 */

public class CBZParser {
	private static final String TAG = CBZParser.class.getSimpleName();

	/**
	 * function converts all the images inside the .cbz file into
	 * link and addes them to spine and linkMap
	 *
	 * @param container   contains implementation for getting raw data from file.
	 * @param publication The `Publication` object resulting from the parsing.
	 */
	public static void parseCBZ(CbzContainer container, Publication publication) {

		publication.getInternalData().put("type", "cbz");
		// since all the image files are inside zip rootpath is kept empty
		publication.getInternalData().put("rootfile", "");

		for (String name : container.getFilesList()) {
			Link link = new Link();
			link.setTypeLink(getMediaType(name));
			link.setHref(name);
			// Add the book images to the spine element
			publication.getSpine().add(link);
			// Add to the resource linkMap for ResourceHandler to publish on the server
			publication.getLinks().add(link);
		}
	}

	/**
	 * Returns the mimetype depending on the file format
	 *
	 * @param name file name
	 * @return mimetype of the input file
	 */
	private static String getMediaType(String name) {
		if (name.contains(".jpg") || name.contains("jpeg")) {
			return "image/jpeg";
		} else if (name.contains("png")) {
			return "image/png";
		} else {
			return "";
		}
	}
}