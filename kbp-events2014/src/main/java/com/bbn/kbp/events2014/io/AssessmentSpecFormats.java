package com.bbn.kbp.events2014.io;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.scoring.Scored;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.bbn.kbp.events2014.ResponseAssessment.MentionType;
import com.bbn.kbp.events2014.io.assessmentCreators.AssessmentCreator;
import com.bbn.kbp.events2014.io.assessmentCreators.RecoveryAssessmentCreator;
import com.bbn.kbp.events2014.io.assessmentCreators.StrictAssessmentCreator;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Handles file formats defined in the KBP 2014 Event Argument Task assessment specifications.

 * These formats represent each event argument as a single line of tab-separated fields.
 * Blank lines and lines beginning with "#" are ignored as comments.  No fields may contain tabs.
 * The columns are as described in the Event Argument Attachment task assessment specification. They are
 * not duplicated here to prevent out-of-sync documentation.
 *
 * @author Ryan Gabbard
 *
 */
public final class AssessmentSpecFormats {
    private static final Logger log = LoggerFactory.getLogger(AssessmentSpecFormats.class);
	private AssessmentSpecFormats() {
		throw new UnsupportedOperationException();
	}

    public enum Format {
        KBP2014 {
            @Override
            public String identifierField(Response response) {
                return Integer.toString(response.old2014ResponseID());
            }

            @Override
            protected Ordering<Response> responseOrdering() {
                return Response.ByOld2014Id;
            }
        }
        , KBP2015 {
            @Override
            public String identifierField(Response response) {
                return response.uniqueIdentifier();
            }

            @Override
            protected Ordering<Response> responseOrdering() {
                return Response.byUniqueIdOrdering();
            }
        };

        protected abstract String identifierField(Response response);

        protected abstract Ordering<Response> responseOrdering();
    }

    /**
     * Creates a directory-based assessment store in the specified directory. Will throw an
     * {@link java.io.IOException} if the directory exists and is non-empty.
     * @param directory
     * @return
     * @throws IOException
     */
	public static AnnotationStore createAnnotationStore(final File directory, Format format) throws IOException {
		if (directory.exists() && !FileUtils.isEmptyDirectory(directory)) {
			throw new IOException(String.format(
				"Non-empty output directory %s when attempting to create assessment store", directory));
		}
		directory.mkdirs();
		return new DirectoryAnnotationStore(directory, StrictAssessmentCreator.create(), false, format);
	}

    /**
     * Opens an existing assessment store stored in the given directory.  The implementation makes some
     * attempt to avoid having multiple assessment store objects writing to the same directory,
     * which can result in data corruption, but this is not guaranteed.
     *
     * @param directory
     * @return
     * @throws IOException
     */
	public static AnnotationStore openAnnotationStore(final File directory, Format format) throws IOException {
		if (!directory.exists() || !directory.isDirectory()) {
            throw new IOException(String.format("Annotation store directory %s either does not exist or is not a directory", directory));
        }
		return new DirectoryAnnotationStore(directory, StrictAssessmentCreator.create(), false, format);
	}

    public static AnnotationStore recoverPossiblyBrokenAnnotationStore(File directory,
                        RecoveryAssessmentCreator assessmentCreator, Format format) throws IOException
    {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IOException(String.format("Annotation store directory %s either does not exist or is not a directory", directory));
        }
        return new DirectoryAnnotationStore(directory, assessmentCreator, false, format);
    }


    public static AnnotationStore openOrCreateAnnotationStore(final File directory,
                                                              Format format) throws IOException
    {
        directory.mkdirs();
        return new DirectoryAnnotationStore(directory, StrictAssessmentCreator.create(), false, format);
    }

    /**
     * Creates a new system output store in the specified directory. If the directory is
     * non-empty, an exception is thrown.
     * @param directory
     * @return
     */
	public static SystemOutputStore createSystemOutputStore(final File directory,
                                                            Format format) throws IOException
    {
		if (directory.exists() && !FileUtils.isEmptyDirectory(directory)) {
            throw new IOException(String.format(
                    "Cannot create system output store: directory is non-empty: %s", directory));
		}
		directory.mkdirs();
		return new DirectorySystemOutputStore(directory, format);
	}

    /**
     * Opens an existing system output store.
     * @param directory
     * @return
     */
	public static SystemOutputStore openSystemOutputStore(final File directory, Format format) {
		checkArgument(directory.exists() && directory.isDirectory(), "Directory to open as annotation store %s either does not exist or is not a directory", directory );
		return new DirectorySystemOutputStore(directory, format);
	}

    public static SystemOutputStore openOrCreateSystemOutputStore(final File directory, Format format) throws IOException {
        if (directory.exists()) {
            return openSystemOutputStore(directory, format);
        } else {
            return createSystemOutputStore(directory, format);
        }
    }

    private static final class DirectorySystemOutputStore implements SystemOutputStore {
		private static final Logger log = LoggerFactory.getLogger(DirectorySystemOutputStore.class);

		private final File directory;
        private final Format format;

		private DirectorySystemOutputStore(final File directory, final Format format) {
			checkArgument(directory.isDirectory(), "Specified directory %s for system output store is not a directory", directory);
			this.directory = checkNotNull(directory);
            this.format = checkNotNull(format);
		}

        private static final Splitter OnTabs = Splitter.on('\t').trimResults();
		@Override
		public SystemOutput read(final Symbol docid) throws IOException {
			final File f = new File(directory, docid.toString());
			if (!f.exists()) {
				throw new FileNotFoundException(String.format("File %s for doc ID %s not found", f.getAbsolutePath(), docid));
			}

			final ImmutableList.Builder<Scored<Response>> ret = ImmutableList.builder();

            int lineNo = 0;
			for (final String line : Files.asCharSource(f, UTF_8).readLines()) {
                ++lineNo;
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				final List<String> parts = ImmutableList.copyOf(OnTabs.split(line));
				try {
                    // we ignore the first field because input system IDs are currently not preserved
                    try {
					    final double confidence = Double.parseDouble(parts.get(10));
					    ret.add(Scored.from(parseArgumentFields(parts.subList(1, parts.size())), confidence));
                    } catch (IndexOutOfBoundsException iobe) {
                        throw new RuntimeException(String.format("Expected 11 tab-separated columns, but got %d", parts.size()), iobe);
                    }
				} catch (final Exception e) {
					throw new RuntimeException(String.format("For doc ID %s, Invalid line %d: %s", docid, lineNo, line), e);
				}
			}

			return SystemOutput.from(docid, ret.build());
		}

		@Override
		public ImmutableSet<Symbol> docIDs() throws IOException {
			return FluentIterable.from(Arrays.asList(directory.listFiles()))
				.transform(FileUtils.ToName)
				.transform(Symbol.FromString)
				.toSet();
		}


		@Override
		public void write(final SystemOutput output) throws IOException {
			final File f = new File(directory, output.docId().toString());
			final PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(f), Charsets.UTF_8)));

			try {
                for (final Response response : format.responseOrdering().sortedCopy(output.responses())) {
					//out.print(response.responseID());
                    out.print(format.identifierField(response));
					out.print("\t");
					final double confidence = output.confidence(response);
					out.print(argToString(response, confidence) + "\n");
				}
			} finally {
				out.close();
			}
		}

		@Override
		public void close() {
			// pass
		}

		private String argToString(final Response arg, final double confidence) {
			final List<String> parts = Lists.newArrayList();
			addArgumentParts(arg, parts, confidence);
			return Joiner.on('\t').join(parts);
		}

		@Override
		public SystemOutput readOrEmpty(final Symbol docid) throws IOException {
			if (docIDs().contains(docid)) {
				return read(docid);
			} else {
				return SystemOutput.from(docid, ImmutableList.<Scored<Response>>of());
			}
		}
	}

	private static void addArgumentParts(final Response arg, final List<String> parts,
			final double confidence)
	{
		parts.add(arg.docID().toString());
		parts.add(arg.type().toString());
		parts.add(arg.role().toString());
		parts.add(cleanString(arg.canonicalArgument().string()));
		parts.add(offsetString(arg.canonicalArgument().charOffsetSpan()));
		parts.add(offsetString(arg.predicateJustifications()));
		parts.add(offsetString(arg.baseFiller()));
		parts.add(offsetString(arg.additionalArgumentJustifications()));
		parts.add(arg.realis().toString());
		parts.add(Double.toString(confidence));
	}

    /**
     * This naively just synchronizes everything to deal with concurrency issues. If it becomes
     * a performance issue we can do something more fine-grained. ~ rgabbard
     */
	private static final class DirectoryAnnotationStore implements AnnotationStore {
		private final File directory;
        private final File lockFile;
        private final LoadingCache<Symbol, AnswerKey> cache;
        private final boolean doCaching;
        private boolean closed = false;
        private final Set<Symbol> docIDs;
        // object which actually creates ResponseAssessments
        // can be used to control how strict we are about
        // the input
        private final AssessmentCreator assessmentCreator;
        private final Format format;

		private DirectoryAnnotationStore(final File directory, AssessmentCreator assessmentCreator,
                                         final boolean doCaching, final Format format) throws IOException
        {
			checkArgument(directory.exists(), "Directory %s for annotation store does not exist", directory);
            // this is a half-hearted attempt at preventing multiple assessment stores
            //  being opened on the same directory at once.  There is a race condition,
            // but we don't anticipate this class being used concurrently enough to justify
            // dealing with it.
            lockFile = new File(directory, "__lock");
            if (lockFile.exists()) {
                throw new IOException(String.format("Directory %s for assessment store is locked; if this is due to a crash, delete %s",
                    directory, lockFile));
            }

			this.directory = checkNotNull(directory);
            this.cache = CacheBuilder.newBuilder().maximumSize(50)
                    .build(new CacheLoader<Symbol, AnswerKey>() {
                        @Override
                        public AnswerKey load(Symbol key) throws Exception {
                            return DirectoryAnnotationStore.this.uncachedRead(key);
                        }
                    });
            this.docIDs = loadInitialDocIds();
            this.assessmentCreator = checkNotNull(assessmentCreator);
            this.doCaching = doCaching;
            this.format = checkNotNull(format);
		}

        @Override
        public synchronized AnswerKey read(final Symbol docid) throws IOException {
            assertNotClosed();
            try {
                if (doCaching) {
                    return cache.get(docid);
                } else {
                    return uncachedRead(docid);
                }
            } catch (ExecutionException e) {
                log.info("Caught exception {}", e);
                if (e.getCause() instanceof IOException) {
                    throw (IOException)e.getCause();
                } else {
                    throw new RuntimeException(e.getCause());
                }
            }
        }

        @Override
        public synchronized Set<Symbol> docIDs() throws IOException {
            assertNotClosed();
            return Collections.unmodifiableSet(docIDs);
        }

		private Set<Symbol> loadInitialDocIds() throws IOException {
            return Sets.newHashSet(FluentIterable.from(Arrays.asList(directory.listFiles()))
                    .transform(FileUtils.ToName)
                    .transform(Symbol.FromString)
                    .toSet());
		}

		@Override
		public synchronized void write(final AnswerKey answerKey) throws IOException {
            assertNotClosed();
            cache.invalidate(answerKey.docId());
            docIDs.add(answerKey.docId());

			final File f = new File(directory, answerKey.docId().toString());
            log.info("Writing assessment for doc ID {}", answerKey.docId());
			final PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(f), Charsets.UTF_8)));

			try {
                // first annotated responses, sorted by response ID
                final Ordering<AssessedResponse> assessedResponseOrdering =
                        format.responseOrdering().onResultOf(AssessedResponse.Response);
                for (final AssessedResponse arg : assessedResponseOrdering.sortedCopy(answerKey.annotatedResponses())) {
					final List<String> parts = Lists.newArrayList();
					parts.add(format.identifierField(arg.response()));
					addArgumentParts(arg.response(), parts, 1.0);
					addAnnotationParts(arg, answerKey.corefAnnotation(), parts);
					out.print(Joiner.on("\t").join(parts)+"\n");
				}
                // then unannotated responses, sorted by reponseID
				for (final Response unannotated : format.responseOrdering().sortedCopy(answerKey.unannotatedResponses())) {
					final List<String> parts = Lists.newArrayList();
					parts.add(format.identifierField(unannotated));
					addArgumentParts(unannotated, parts, 1.0);
					addUnannotatedAnnotationParts(unannotated, answerKey.corefAnnotation(), parts);
					out.print(Joiner.on("\t").join(parts) + "\n");
				}
			} finally {
				out.close();
			}
		}

		@Override
		public synchronized void close() {
            closed = true;
			lockFile.delete();
		}

		@Override
		public synchronized AnswerKey readOrEmpty(final Symbol docid) throws IOException {
            assertNotClosed();
			if (docIDs().contains(docid)) {
				return read(docid);
			} else {
				return AnswerKey.createEmpty(docid);
			}
		}

        private synchronized AnswerKey uncachedRead(final Symbol docid) throws IOException {
            final ImmutableList.Builder<AssessedResponse> annotated = ImmutableList.builder();
            final ImmutableList.Builder<Response> unannotated = ImmutableList.builder();
            final CorefAnnotation.Builder corefBuilder = assessmentCreator.corefBuilder(docid);

            final CharSource source = Files.asCharSource(new File(directory, docid.toString()), UTF_8);
            for (final String line : source.readLines()) {
                try {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    final String[] parts = line.split("\t");
                    final List<String> argumentParts = Arrays.asList(parts).subList(1, 11);
                    final List<String> annotationParts = Arrays.asList(parts).subList(11, parts.length);

                    if (annotationParts.isEmpty()) {
                        throw new IOException(String.format(
                                "The assessment store file for document ID %s appears to be a system "+
                                        "output file with no assessment columns.", docid));
                    }

                    final Response response = parseArgumentFields(argumentParts);
                    final AssessmentCreator.AssessmentParseResult annotation = parseAnnotation(annotationParts);

                    if (annotation.assessment().isPresent()) {
                        annotated.add(AssessedResponse.from(response, annotation.assessment().get()));
                    } else {
                        unannotated.add(response);
                    }

                    if (annotation.corefId().isPresent()) {
                        corefBuilder.corefCAS(response.canonicalArgument(), annotation.corefId().get());
                    } else {
                        corefBuilder.addUnannotatedCAS(response.canonicalArgument());
                    }
                } catch (Exception e) {
                    throw new IOException(String.format(
                            "While reading answer key for document %s, error on line %s", docid, line), e);
                }
            }

            return assessmentCreator.createAnswerKey(docid, annotated.build(), unannotated.build(),
                    corefBuilder.build());
        }

        private static final Set<String> emptyCorefEncodings = ImmutableSet.of("NIL", "", "UNANNOTATED");
        private AssessmentCreator.AssessmentParseResult parseAnnotation(final List<String> parts) {
            checkArgument(parts.size() == 7, "Expected parts of size 7, but got %s", parts);

            final Optional<Integer> coreference = emptyCorefEncodings.contains(parts.get(4))
                    ?Optional.<Integer>absent():Optional.of(Integer.parseInt(parts.get(4)));

            if (parts.contains("UNANNOTATED")) {
                return AssessmentCreator.AssessmentParseResult.fromCorefOnly(coreference);
            }

            final Optional<FieldAssessment> AET = FieldAssessment.parseOptional(parts.get(0));
            final Optional<FieldAssessment> AER = FieldAssessment.parseOptional(parts.get(1));
            final Optional<FieldAssessment> casAssessment = FieldAssessment.parseOptional(parts.get(2));
            final Optional<FieldAssessment> baseFillerAssessment = FieldAssessment.parseOptional(parts.get(3));

            final Optional<KBPRealis> realis = KBPRealis.parseOptional(parts.get(5));
            final Optional<MentionType> mentionTypeOfCAS = MentionType.parseOptional(parts.get(6));

            return assessmentCreator.createAssessmentFromFields(AET, AER, casAssessment,
                    realis, baseFillerAssessment, coreference, mentionTypeOfCAS);
        }

        private static void addAnnotationParts(final AssessedResponse assessedResponse,
                  final CorefAnnotation corefAnnotation, final List<String> parts)
        {
            final ResponseAssessment ann = assessedResponse.assessment();
			parts.add(FieldAssessment.asCharacterOrNil(ann.justificationSupportsEventType()));
			parts.add(FieldAssessment.asCharacterOrNil(ann.justificationSupportsRole()));
			parts.add(FieldAssessment.asCharacterOrNil(ann.entityCorrectFiller()));
			parts.add(FieldAssessment.asCharacterOrNil(ann.baseFillerCorrect()));

            final Optional<Integer> corefId = corefAnnotation.corefId(
                    assessedResponse.response().canonicalArgument());
			if (corefId.isPresent()) {
				parts.add(Integer.toString(corefId.get()));
			} else {
				parts.add("NIL");
			}
			parts.add(KBPRealis.asString(ann.realis()));
			parts.add(MentionType.stringOrNil(ann.mentionTypeOfCAS()));
		}

        private static final String UNANNOTATED = "UNANNOTATED";
        private static void addUnannotatedAnnotationParts(Response unannotated, CorefAnnotation corefAnnotation,
                                                          final List<String> parts)
        {
            parts.addAll(Collections.nCopies(4, UNANNOTATED));
            final Optional<Integer> corefId = corefAnnotation.corefId(unannotated.canonicalArgument());
            if (corefId.isPresent()) {
                parts.add(corefId.get().toString());
            } else {
                parts.add(UNANNOTATED);
            }
            parts.addAll(Collections.nCopies(2, UNANNOTATED));
		}

        private synchronized void assertNotClosed() {
            if (closed) {
                throw new RuntimeException("Illegal attempt to use a closed assessment store.");
            }
        }
	}

	private static String offsetString(final Set<CharOffsetSpan> spans) {
        if (spans.isEmpty()) {
            return "NIL";
        }

		final List<String> ret = Lists.newArrayList();

		for (final CharOffsetSpan span : spans) {
			ret.add(offsetString(span));
		}
		return StringUtils.CommaJoiner.join(ret);
	}

	private static String offsetString(final CharOffsetSpan span) {
		return String.format("%d-%d", span.startInclusive(), span.endInclusive());
	}

	private static String cleanString(String s) {
		s = s.replace('\t', ' ');
		s = s.replace("\r\n", " ");
		s = s.replace('\n', ' ');
		return s;
	}

	public static Response parseArgumentFields(final List<String> parts) {
		return Response.createFrom(Symbol.from(parts.get(0)),
			Symbol.from(parts.get(1)), Symbol.from(parts.get(2)),
			KBPString.from(parts.get(3), parseCharOffsetSpan(parts.get(4))),
			parseCharOffsetSpan(parts.get(6)),
			parseCharOffsetSpans(parts.get(7)),
			parseCharOffsetSpans(parts.get(5)),
			KBPRealis.parse(parts.get(8)));
	}

	private static CharOffsetSpan parseCharOffsetSpan(final String s) {
		final String[] parts = s.split("-");
		if (parts.length != 2) {
			throw new RuntimeException(String.format("Invalid span %s", s));
		}
		return CharOffsetSpan.fromOffsetsOnly(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
	}

    private static final Splitter onCommas =
            Splitter.on(",").trimResults().omitEmptyStrings();
	private static ImmutableSet<CharOffsetSpan> parseCharOffsetSpans(final String s) {
        if ("NIL".equals(s)) {
            return ImmutableSet.of();
        }

		final ImmutableSet.Builder<CharOffsetSpan> ret = ImmutableSet.builder();

		for (final String span : onCommas.split(s)) {
			ret.add(parseCharOffsetSpan(span));
		}

		final ImmutableSet<CharOffsetSpan> spans = ret.build();

        if (spans.isEmpty()) {
            throw new RuntimeException(String.format("Empty spans sets must be indicated by NIL"));
        }

        return spans;
	}


}
