package io.github.wlk.jlibrus.client.model;

/**
 * Grade category with its weight.
 *
 * @param weight          may be {@code null} when Librus omits it
 * @param countsToAverage may be {@code null} when Librus omits it
 */
public record GradeCategory(long id, String name, Integer weight, Boolean countsToAverage, Integer colorId) {
}
