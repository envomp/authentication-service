package ee.taltech.arete_admin_panel.service;

import arete.java.AreteClient;
import arete.java.request.AreteRequest;
import arete.java.request.AreteTestUpdate;
import arete.java.response.ConsoleOutput;
import arete.java.response.Error;
import arete.java.response.SystemState;
import arete.java.response.TestContext;
import arete.java.response.UnitTest;
import arete.java.response.*;
import ee.taltech.arete_admin_panel.domain.*;
import ee.taltech.arete_admin_panel.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional()
@EnableAsync
public class AreteService {

	private final Logger LOG = LoggerFactory.getLogger(this.getClass());
	private final CacheService cacheService;

	private final StudentRepository studentRepository;
	private final CourseRepository courseRepository;
	private final SlugRepository slugRepository;
	private final SubmissionRepository submissionRepository;
	private final JobRepository jobRepository;

	private AreteClient areteClient = new AreteClient(System.getProperty("TESTER_URL", "http://localhost:8098"));
	private Queue<AreteResponse> jobQueue = new LinkedList<>();
	private int antiStuckQueue = 20;
	private Boolean halted = false;

	public AreteService(CacheService cacheService, StudentRepository studentRepository, CourseRepository courseRepository, SlugRepository slugRepository, JobRepository jobRepository, SubmissionRepository submissionRepository) {
		this.cacheService = cacheService;
		this.studentRepository = studentRepository;
		this.courseRepository = courseRepository;
		this.slugRepository = slugRepository;
		this.jobRepository = jobRepository;
		this.submissionRepository = submissionRepository;
	}

	public void enqueueAreteResponse(AreteResponse response) {
		LOG.info("Saving job into DB for user: {} with hash: {} in: {} where queue has {} elements", response.getUniid(), response.getHash(), response.getRoot(), jobQueue.size());
		jobQueue.add(response);
	}

	@Async
	@Scheduled(fixedRate = 100)
	public void asyncRunJob() {

		AreteResponse response = jobQueue.poll();

		if (response != null) {
			if (!halted) {
				try {
					halted = true;
					parseAreteResponse(response);
				} catch (Exception ignored) {
				} finally {
					halted = false;
					antiStuckQueue = 20;
				}
			} else {
				jobQueue.add(response);
				antiStuckQueue -= 1;
			}
		}

		if (antiStuckQueue <= 0) {
			antiStuckQueue = 20;
			halted = false;
		}
	}

	public void parseAreteResponse(AreteResponse response) {
		setDefaultValuesIfNull(response);
		saveSubmission(response);
		saveJob(response);

		if (!response.getFailed()) {
			LOG.debug("getting course");
			Course course = getCourse(response);

			LOG.debug("getting slug");
			Slug slug = getSlug(response, course);

			LOG.debug("getting student");
			Student student = getStudent(response, course, slug);

			LOG.debug("update all");
			updateStudentSlugCourse(response, student, slug, course);
		}

	}

	private void setDefaultValuesIfNull(AreteResponse response) {
		if (response.getUniid() == null) {
			response.setUniid("NaN");
		}

		if (response.getErrors() == null) {
			response.setErrors(new ArrayList<>());
		}

		if (response.getFiles() == null) {
			response.setFiles(new ArrayList<>());
		}

		if (response.getTestFiles() == null) {
			response.setTestFiles(new ArrayList<>());
		}

		if (response.getTestSuites() == null) {
			response.setTestSuites(new ArrayList<>());
		}

		if (response.getConsoleOutputs() == null) {
			response.setConsoleOutputs(new ArrayList<>());
		}

		if (response.getOutput() == null) {
			response.setOutput("no output");
		}
	}

	private void updateStudentSlugCourse(AreteResponse response, Student student, Slug slug, Course course) {

		if (response.getStyle() == 100) {
			slug.setCommitsStyleOK(slug.getCommitsStyleOK() + 1);
			course.setCommitsStyleOK(course.getCommitsStyleOK() + 1);
			student.setCommitsStyleOK(student.getCommitsStyleOK() + 1);
		}

		int newDiagnosticErrors = response.getErrors().size();
		Map<String, Long> diagnosticErrors = response.getErrors().stream().map(Error::getKind).collect(Collectors.groupingBy(e -> e, Collectors.counting()));

		for (String key : diagnosticErrors.keySet()) {

			updateDiagnosticCodeErrors(diagnosticErrors, key, slug.getDiagnosticCodeErrors());
			updateDiagnosticCodeErrors(diagnosticErrors, key, course.getDiagnosticCodeErrors());
			updateDiagnosticCodeErrors(diagnosticErrors, key, student.getDiagnosticCodeErrors());
		}

		int newTestErrors = 0;
		int newTestPassed = 0;
		int newTestsRan = 0;

		Map<String, Integer> testErrors = new HashMap<>();

		for (TestContext testContext : response.getTestSuites()) {
			for (UnitTest unitTest : testContext.getUnitTests()) {
				newTestsRan += 1;
				if (unitTest.getStatus().equals(UnitTest.TestStatus.FAILED)) {
					newTestErrors += 1;
					if (testErrors.containsKey(unitTest.getExceptionClass())) {
						testErrors.put(unitTest.getExceptionClass(), testErrors.get(unitTest.getExceptionClass()) + 1);
					} else {
						testErrors.put(unitTest.getExceptionClass(), 1);
					}
				}
				if (unitTest.getStatus().equals(UnitTest.TestStatus.PASSED)) {
					newTestPassed += 1;
				}
			}
		}

		for (String key : testErrors.keySet()) {

			updateCodeErrors(testErrors, key, slug.getTestCodeErrors());
			updateCodeErrors(testErrors, key, course.getTestCodeErrors());
			updateCodeErrors(testErrors, key, student.getTestCodeErrors());
		}

		slug.setTotalCommits(slug.getTotalCommits() + 1);
		course.setTotalCommits(course.getTotalCommits() + 1);
		student.setTotalCommits(student.getTotalCommits() + 1);

		slug.setTotalDiagnosticErrors(slug.getTotalDiagnosticErrors() + newDiagnosticErrors);
		course.setTotalDiagnosticErrors(course.getTotalDiagnosticErrors() + newDiagnosticErrors);
		student.setTotalDiagnosticErrors(student.getTotalDiagnosticErrors() + newDiagnosticErrors);

		slug.setTotalTestErrors(slug.getTotalTestErrors() + newTestErrors);
		course.setTotalTestErrors(course.getTotalTestErrors() + newTestErrors);
		student.setTotalTestErrors(student.getTotalTestErrors() + newTestErrors);

		slug.setTotalTestsPassed(slug.getTotalTestsPassed() + newTestPassed);
		course.setTotalTestsPassed(course.getTotalTestsPassed() + newTestPassed);
		student.setTotalTestsPassed(student.getTotalTestsPassed() + newTestPassed);

		slug.setTotalTestsRan(slug.getTotalTestsRan() + newTestsRan);
		course.setTotalTestsRan(course.getTotalTestsRan() + newTestsRan);
		student.setTotalTestsRan(student.getTotalTestsRan() + newTestsRan);

		student.getTimestamps().add(response.getTimestamp());
		student.setLastTested(response.getTimestamp());


		student.setDifferentCourses(student.getCourses().size());
		student.setDifferentSlugs(student.getSlugs().size());

		updateCourse(course, course.getId());
		updateSlug(slug, slug.getId());
		updateStudent(student, student.getId());

	}

	private void updateCodeErrors(Map<String, Integer> testErrors, String key, Set<CodeError> testCodeErrors) {
		if (testCodeErrors.stream().anyMatch(x -> x.getErrorType().equals(key))) {
			testCodeErrors.stream().filter(error -> error.getErrorType().equals(key)).forEachOrdered(error -> error.setRepetitions(Math.toIntExact(error.getRepetitions() + testErrors.get(key))));
		} else {
			testCodeErrors.add(new CodeError(key, Math.toIntExact(testErrors.get(key))));
		}
	}

	private void updateDiagnosticCodeErrors(Map<String, Long> diagnosticErrors, String key, Set<CodeError> diagnosticCodeErrors) {
		if (diagnosticCodeErrors.stream().anyMatch(x -> x.getErrorType().equals(key))) {
			for (CodeError error : diagnosticCodeErrors) {
				if (error.getErrorType().equals(key)) {
					error.setRepetitions(Math.toIntExact(error.getRepetitions() + diagnosticErrors.get(key)));
				}
			}
		} else {
			diagnosticCodeErrors.add(new CodeError(key, Math.toIntExact(diagnosticErrors.get(key))));
		}
	}

	private Slug getSlug(AreteResponse response, Course course) {
		Slug slug;
		Optional<Slug> optionalSlug = slugRepository.findByCourseUrlAndName(course.getGitUrl(), response.getSlug());
		slug = optionalSlug.orElseGet(() -> Slug.builder()
				.courseUrl(course.getGitUrl())
				.name(response.getSlug())
				.build());

		return slug;
	}

	private Course getCourse(AreteResponse response) {
		Course course;
		Optional<Course> optionalCourse = courseRepository.findByGitUrl(response.getGitTestRepo());
		course = optionalCourse.orElseGet(() -> Course.builder()
				.gitUrl(response.getGitTestRepo())
				.name(response.getRoot())
				.build());

		return course;
	}

	private Student getStudent(AreteResponse response, Course course, Slug slug) {
		Student student;
		Optional<Student> optionalStudent = studentRepository.findByUniid(response.getUniid());
		student = optionalStudent.orElseGet(() -> Student.builder()
				.uniid(response.getUniid())
				.firstTested(response.getTimestamp())
				.lastTested(response.getTimestamp())
				.build());

		if (student.getGitRepo() == null && response.getGitStudentRepo() != null) {
			student.setGitRepo(response.getGitTestRepo());
		}

		student.getCourses().add(course.getGitUrl());
		student.getSlugs().add(slug.getName());
		return student;
	}

	private void saveJob(AreteResponse response) {
		Job job = Job.builder()
				.output(response.getOutput().replace("\n", "<br>"))
				.consoleOutput(response.getConsoleOutputs().stream().map(ConsoleOutput::getContent).collect(Collectors.joining()).replace("\n", "<br>"))
				.testSuites(response.getTestSuites().stream()
						.map(x -> ee.taltech.arete_admin_panel.domain.TestContext.builder()
								.endDate(x.getEndDate())
								.file(x.getFile())
								.grade(x.getGrade())
								.name(x.getName())
								.passedCount(x.getPassedCount())
								.startDate(x.getStartDate())
								.weight(x.getWeight())
								.unitTests(
										x.getUnitTests().stream()
												.map(y -> ee.taltech.arete_admin_panel.domain.UnitTest.builder()
														.exceptionClass(y.getExceptionClass())
														.exceptionMessage(y.getExceptionMessage())
														.groupsDependedUpon(y.getGroupsDependedUpon())
														.methodsDependedUpon(y.getMethodsDependedUpon())
														.printExceptionMessage(y.getPrintExceptionMessage())
														.printStackTrace(y.getPrintStackTrace())
														.stackTrace(y.getStackTrace())
														.name(y.getName())
														.status(y.getStatus().toString())
														.timeElapsed(y.getTimeElapsed())
														.weight(y.getWeight())
														.build())
												.collect(Collectors.toList()))
								.build())
						.collect(Collectors.toList()))
				.timestamp(response.getTimestamp())
				.uniid(response.getUniid())
				.slug(response.getSlug())
				.root(response.getRoot())
				.testingPlatform(response.getTestingPlatform())
				.priority(response.getPriority())
				.hash(response.getHash())
				.commitMessage(response.getCommitMessage())
				.failed(response.getFailed())
				.gitStudentRepo(response.getGitStudentRepo())
				.gitTestRepo(response.getGitTestRepo())
				.dockerTimeout(response.getDockerTimeout())
				.dockerExtra(response.getDockerExtra())
				.systemExtra(response.getSystemExtra())
				.build();

		LOG.info("Saving job");
		jobRepository.save(job);
	}

	public Submission saveSubmission(AreteResponse response) {
		Submission submission = Submission.builder()
				.uniid(response.getUniid())
				.slug(response.getSlug())
				.hash(response.getHash())
				.testingPlatform(response.getTestingPlatform())
				.root(response.getRoot())
				.timestamp(response.getTimestamp())
				.gitStudentRepo(response.getGitStudentRepo())
				.gitTestSource(response.getGitTestRepo())
				.build();

		updateSubmissions(submission, submission.getHash());
		return submission;
	}

	public AreteResponse makeRequestSync(AreteRequest areteRequest) {
		LOG.info("Forwarding a sync submission: {}", areteRequest);
		return areteClient.requestSync(areteRequest);
	}

	public void makeRequestAsync(AreteRequest areteRequest) {
		LOG.info("Forwarding a async submission: {}", areteRequest);
		areteClient.requestAsync(areteRequest);
	}

	public void makeRequestWebhook(AreteTestUpdate update, String testRepository) {
		AreteTestUpdate.Commit latest = update.getCommits().get(0);

		Set<String> slugs = new HashSet<>();
		slugs.addAll(latest.getAdded());
		slugs.addAll(latest.getModified());

		AreteRequest request = AreteRequest.builder()
				.eMail(latest.getAuthor().getEmail())
				.uniid(latest.getAuthor().getName())
				.gitStudentRepo(update.getProject().getUrl())
				.gitTestSource(testRepository)
				.slugs(slugs)
				.build();

		makeRequestAsync(request);
	}

	public void updateImage(String image) {
		LOG.info("Updating image: {}", image);
		areteClient.updateImage(image);
	}

	public void updateTests(AreteTestUpdate areteTestUpdate) {
		LOG.info("Updating tests: {}", areteTestUpdate);
		areteClient.updateTests(areteTestUpdate);
	}

	public String getTesterLogs() {
		LOG.info("Reading tester logs");
		return areteClient.requestLogs();
	}

	public SystemState getTesterState() {
		LOG.info("Reading tester state");
		return areteClient.requestState();
	}

	public AreteRequest[] getActiveSubmissions() {
		LOG.info("Reading all active submissions");
		return areteClient.requestActiveSubmissions();
	}

	/////// CACHING

	public Course updateCourse(Course course, Long id) {
		LOG.info("Updating course cache: {}", id);
		courseRepository.save(course);
		cacheService.updateCourseList(course);
		return course;
	}

	public Slug updateSlug(Slug slug, Long id) {
		LOG.info("Updating slug cache: {}", id);
		slugRepository.save(slug);
		cacheService.updateSlugList(slug);
		return slug;
	}

	public Student updateStudent(Student student, Long id) {
		LOG.info("Updating student cache: {}", id);
		studentRepository.save(student);
		cacheService.updateStudentList(student);
		return student;
	}

	public void updateSubmissions(Submission submission, String hash) {
		LOG.info("Updating submission cache: {}", hash);
		submissionRepository.save(submission);
		cacheService.updateSubmissionList(submission);
	}

}
