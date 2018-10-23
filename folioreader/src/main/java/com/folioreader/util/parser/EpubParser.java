package com.folioreader.util.parser;

import org.readium.r2.streamer.container.Container;
import org.readium.r2.streamer.container.ContainerCbz;
import org.readium.r2.streamer.container.ContainerEpub;
import org.readium.r2.shared.Publication;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by Shrikant Badwaik on 27-Jan-17.
 */

public class EpubParser {
	private final String TAG = "EpubParser";

	private Container container;        //can be either EpubContainer or DirectoryContainer
	private Publication publication;
	//private String epubVersion;

	public static class EpubParserException extends Exception {

		public EpubParserException(){}

		public  EpubParserException(String msg) {super(msg);}

	}

	public EpubParser(Container container) {
		this.container = container;
		this.publication = new Publication();
	}

	public Publication parseEpubFile(String filePath) throws ParserConfigurationException {
		String rootFile;
		try {
			if (filePath.contains(".cbz")) {
				CBZParser.parseCBZ( (ContainerCbz) container, publication);
				return publication;
			}
			if (isMimeTypeValid()) {
				rootFile = parseContainer();

				publication.getInternalData().put("type", "epub");
				publication.getInternalData().put("rootfile", rootFile);
				//Parse OPF file
				//this.publication = OPFParser.parseOpfFile(rootFile, this.publication, container);
				// Parse Encryption
				//this.publication.encryptions = EncryptionParser.parseEncryption(container);
				// Parse Media Overlay
				MediaOverlayParser.parseMediaOverlay(this.publication, (ContainerEpub) container);
				return publication;
			}
		} catch (ParserConfigurationException | EpubParserException e) {
			System.out.println(TAG + " parserEpubFile() error " + e.toString());
			throw new ParserConfigurationException("Unable to parse Epub File");
		}
		return null;
	}

	private boolean isMimeTypeValid() throws ParserConfigurationException {
		String mimeTypeData = container.data("mimetype").toString();

		if (mimeTypeData != null && mimeTypeData.equals("application/epub+zip")) {
			return true;
		} else {
			System.out.println(TAG + "Invalid MIME type: " + mimeTypeData);
			throw new ParserConfigurationException("Invalid MIME type");
		}
	}

	private String parseContainer() throws EpubParserException {
		String containerPath = "META-INF/container.xml";
		String containerData = container.data(containerPath).toString();

		if (containerData == null) {
			System.out.println(TAG + " File is missing: " + containerPath);
			throw new EpubParserException("File is missing");
		}

		String opfFile = containerXmlParser(containerData);
		if (opfFile == null) {
			throw new EpubParserException("Error while parsing");
		}
		return opfFile;
	}

	//@Nullable
	private String containerXmlParser(String containerData) throws EpubParserException {           //parsing container.xml
		try {
			String xml = containerData.replaceAll("[^\\x20-\\x7e]", "").trim();         //in case encoding problem

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);

			DocumentBuilder builder = factory.newDocumentBuilder();

			Document document = builder.parse(new InputSource(new StringReader(xml)));
			document.getDocumentElement().normalize();

			if (document == null) {
				throw new EpubParserException("Error while parsing container.xml");
			}

			Element rootElement = (Element) ((Element) document.getDocumentElement().getElementsByTagNameNS("*", "rootfiles").item(0)).getElementsByTagNameNS("*", "rootfile").item(0);
			if (rootElement != null) {
				String opfFile = rootElement.getAttribute("full-path");
				if (opfFile == null) {
					throw new EpubParserException("Missing root file element in container.xml");
				}

				return opfFile;                    //returns opf file
			}
		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}
		return null;
	}


	//@Nullable
	public static Document xmlParser(String xmlData) throws EpubParserException {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);

			DocumentBuilder builder = factory.newDocumentBuilder();

			StringReader characterStream = new StringReader(xmlData);
			InputSource inputSource = new InputSource(characterStream);
			Document document = builder.parse(inputSource);
			document.getDocumentElement().normalize();

			if (document == null) {
				throw new EpubParserException("Error while parsing xml file");
			}

			return document;
		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}
		return null;
	}


}