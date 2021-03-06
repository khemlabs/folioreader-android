package com.folioreader.util.parser;

import org.readium.r2.shared.MediaOverlays;
import org.readium.r2.streamer.container.ContainerEpub;
import org.readium.r2.shared.Publication;
import org.readium.r2.shared.MediaOverlayNode;
import org.readium.r2.shared.Link;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by gautam chibde on 31/5/17.
 */

public class MediaOverlayParser {

	/**
	 * Looks for the link with type: application/smil+xml and parsed the data as
	 * media-overlay also adds link for media-overlay for specific file
	 *
	 * @param publication The `Publication` object resulting from the parsing.
	 * @param container   contains implementation for getting raw data from file
	 * @throws EpubParser.EpubParserException if file is invalid for not found
	 */
	public static void parseMediaOverlay(Publication publication, ContainerEpub container)
			throws EpubParser.EpubParserException {
		for (Link link : publication.getLinks()) {
			if (link.getTypeLink().equalsIgnoreCase("application/smil+xml")) {
				String smip = new String(container.data(link.getHref()));
				if (smip == null)
					return; // maybe file is invalid

				Document document = EpubParser.xmlParser(smip);

				if (document == null)
					throw new EpubParser.EpubParserException("Error while parsing file " + link.getHref());

				Element body = (Element) document.getDocumentElement().getElementsByTagNameNS("*", "body").item(0);

				MediaOverlayNode node = new MediaOverlayNode();
				node.getRole().add("section");

				if (body.hasAttribute("epub:textref"))
					node.setText(body.getAttribute("epub:textref"));

				parseParameters(body, node, link.getHref());
				parseSequences(body, node, publication, link.getHref());

				// TODO
				// Body attribute epub:textref is optional
				// ref
				// https://www.idpf.org/epub/30/spec/epub30-mediaoverlays.html#sec-smil-body-elem
				// need to handle <seq> parsing in an alternate way

				if (node.getText() != null) {
					String baseHref = node.getText().split("#")[0];
					int position = getPosition(publication.getSpine(), baseHref);

					if (position != -1) {
						addMediaOverlayToSpine(publication, node, position);
					}
				} else {
					for (MediaOverlayNode node1 : node.getChildren()) {
						int position = getPosition(publication.getSpine(), node1.getText());
						if (position != -1) {
							addMediaOverlayToSpine(publication, node1, position);
						}
					}
				}
			}
		}
	}

	/**
	 * [RECURSIVE]
	 * <p>
	 * Parse the <seq> elements at the current XML level. It will recursively parse
	 * their children's <par> and <seq>
	 *
	 * @param body input element with seq tag
	 * @param node contains parsed <seq><par></par></seq> elements
	 * @param href path of SMILUtil file
	 */
	private static void parseSequences(Element body, MediaOverlayNode node, Publication publication, String href)
			throws StackOverflowError {
		if (body == null || !body.hasChildNodes()) {
			return;
		}
		for (Node n = body.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getTagName().equalsIgnoreCase("seq")) {
					MediaOverlayNode mediaOverlayNode = new MediaOverlayNode();

					if (e.hasAttribute("epub:textref"))
						mediaOverlayNode.setText(e.getAttribute("epub:textref"));

					mediaOverlayNode.getRole().add("section");

					// child <par> elements in seq
					parseParameters(e, mediaOverlayNode, href);
					node.getChildren().add(mediaOverlayNode);
					// recur to parse child node elements
					parseSequences(e, mediaOverlayNode, publication, href);

					if (node.getText() == null)
						return;

					// Not clear about the IRI reference, epub:textref in seq may not have [ "#"
					// ifragment ]
					// ref:-
					// https://www.idpf.org/epub/30/spec/epub30-mediaoverlays.html#sec-smil-seq-elem
					// TODO is it req? code ref from
					// https://github.com/readium/r2-streamer-swift/blob/feature/media-overlay/Sources/parser/SMILParser.swift
					// can be done with contains?

					String baseHrefParent = node.getText();
					if (node.getText().contains("#")) {
						baseHrefParent = node.getText().split("#")[0];
					}
					if (mediaOverlayNode.getText().contains("#")) {
						String baseHref = mediaOverlayNode.getText().split("#")[0];

						if (!baseHref.equals(baseHrefParent)) {
							int position = getPosition(publication.getSpine(), baseHref);

							if (position != -1)
								addMediaOverlayToSpine(publication, mediaOverlayNode, position);
						}
					}
				}
			}
		}
	}

	/**
	 * Parse the <par> elements at the current XML element level.
	 *
	 * @param body input element with seq tag
	 * @param node contains parsed <par></par> elements
	 */
	private static void parseParameters(Element body, MediaOverlayNode node, String href) {
		NodeList par = body.getElementsByTagNameNS("*", "par");
		if (par.getLength() == 0) {
			return;
		}
		// For each <par> in the current scope.
		for (Node n = body.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				Element e = (Element) n;
				if (e.getTagName().equalsIgnoreCase("par")) {
					MediaOverlayNode mediaOverlayNode = new MediaOverlayNode();
					Element text = (Element) e.getElementsByTagNameNS("*", "text").item(0);
					Element audio = (Element) e.getElementsByTagNameNS("*", "audio").item(0);

					if (text != null)
						mediaOverlayNode.setText(text.getAttribute("src"));
					if (audio != null) {
						// mediaOverlayNode.audio = SMILParser.parseAudio(audio, href);
					}
					node.getChildren().add(mediaOverlayNode);
				}
			}
		}
	}

	/**
	 * Add parsed media-overlay object to corresponding spine item
	 *
	 * @param publication publication object
	 * @param node        parsed media overlay node
	 * @param position    position on spine item in publication
	 */
	private static void addMediaOverlayToSpine(Publication publication, MediaOverlayNode node, int position) {
		Link link = publication.getSpine().get(position);
		// Add new node to MediaOverlay
		publication.getSpine().get(position).getMediaOverlays().getMediaOverlaysNodes().add(node);
		// Add new properties
		publication.getSpine().get(position).getProperties().getContains().add("media-overlay?resource=" + link.getHref());
		// Add new link
		Link newLink = new Link();
		newLink.setHref("8080/media-overlay?resource=" + link.getHref());
		newLink.getRel().add("media-overlay");
		newLink.setTypeLink("application/vnd.readium.mo+json");
		publication.getLinks().add(newLink);
	}

	/**
	 * returns position of the spine whose href equals baseHref
	 *
	 * @param spines   spine list in publication
	 * @param baseHref name of the file which corresponding to media-overlay
	 * @return returns position of the spine item
	 */
	private static int getPosition(List<Link> spines, String baseHref) {
		for (Link link : spines) {
			int offset = link.getHref().indexOf("/", 0);
			int startIndex = link.getHref().indexOf("/", offset + 1);
			String path = link.getHref().substring(startIndex + 1);
			if (baseHref.contains(path)) {
				return spines.indexOf(link);
			}
		}
		return -1;
	}
}