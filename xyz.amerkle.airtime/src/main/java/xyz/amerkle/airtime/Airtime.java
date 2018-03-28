package xyz.amerkle.airtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Airtime {

	public static void main(String[] args) throws IOException {
		Path path = null;
		if (args.length != 1) {
			path = Paths.get(".");
		} else {
			path = Paths.get(args[0]);
		}

		Files.list(path)
				.filter(Files::isDirectory)
				.forEach(Airtime::handleYears);
		handleYears(path);
	}

	private static void handleYears(Path p) {
		try {
			System.out.println("======= " + p.getName(p.getNameCount() - 1) + " =======");
			long count = Files.walk(p)
					.filter(p1 -> p1.toString()
							.endsWith("igc"))
					.count();
			System.out.println("Number of flights:\t" + count);
			if (count == 0)
				return;
			Duration time = Files.walk(p)
					.filter(p1 -> p1.toString()
							.endsWith("igc"))
					.map(Airtime::handleFlightTime)
					.reduce(Duration.ZERO, (d1, d2) -> d1.plus(d2));
			double distance = Files.walk(p)
					.filter(p1 -> p1.toString()
							.endsWith("igc"))
					.map(Airtime::handleDistance)
					.mapToDouble(d -> d)
					.sum();
			Map<String, Long> collect = Files.walk(p)
					.filter(p1 -> p1.toString()
							.endsWith("igc"))
					.map(Airtime::getLocations)
					.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
			System.out.println("flight time:\t\t" + formatDuration(time));
			System.out.println("average flight time:\t" + formatDuration(time.dividedBy(count)));
			System.out.println("distance:\t\t" + distance + "km");
			System.out.println(String.format("average speed:\t\t%.2fkm/h", (distance / (time.getSeconds() / 3600))));
			System.out.println("locations:");
			collect.entrySet()
					.stream()
					.sorted((e1, e2) -> e2.getValue()
							.compareTo(e1.getValue()))
					.forEach(e -> System.out.println("\t\t\t" + e.getKey() + ": " + e.getValue()));
			System.out.println();
			Files.walk(p)
					.filter(p1 -> p1.toString()
							.endsWith("igc"))
					.forEach(Airtime::findDummyLogs);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private static void findDummyLogs(Path path) {
		try {
			long size = Files.readAttributes(path, BasicFileAttributes.class)
					.size();
			if (size < 5000) {
				System.out.println("possible dummy log: " + path.toString());
			}
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private static Duration handleFlightTime(Path path) {
		try {
			Optional<String> first0 = Files.lines(path, StandardCharsets.ISO_8859_1)
					.filter(p -> p.startsWith("B"))
					.findFirst();
			String last = Files.lines(path, StandardCharsets.ISO_8859_1)
					.filter(p -> p.startsWith("B"))
					.reduce("", (s1, s2) -> s1.compareTo(s2) > 0 ? s1 : s2);
			String first = first0.orElseThrow(RuntimeException::new);
			DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("kkmmss");
			LocalTime parseFirst = LocalTime.parse(first.substring(1, 7), dateTimeFormatter);
			LocalTime parseLast = LocalTime.parse(last.substring(1, 7), dateTimeFormatter);
			return Duration.between(parseFirst, parseLast);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private static String getLocations(Path path) {
		try {
			return Files.lines(path, StandardCharsets.ISO_8859_1)
					.filter(line -> line.startsWith("HPSITSITE"))
					.flatMap(line -> Stream.of(line.split(":")))
					.skip(1)
					.map(s -> s.trim())
					.findFirst()
					.get();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	private static Double handleDistance(Path path) {
		try {
			Optional<String> distance = Files.lines(path, StandardCharsets.ISO_8859_1)
					.filter(p -> p.startsWith("LXSX;"))
					.flatMap(s -> Stream.of(s.split(";")))
					.filter(s -> s.startsWith("Dist"))
					.flatMap(s -> Stream.of(s.split(":")))
					.skip(1)
					.findFirst();

			return Double.parseDouble(distance.get());
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static String formatDuration(Duration duration) {
		long seconds = duration.getSeconds();
		long absSeconds = Math.abs(seconds);
		String positive = String.format("%d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);
		return seconds < 0 ? "-" + positive : positive;
	}
}