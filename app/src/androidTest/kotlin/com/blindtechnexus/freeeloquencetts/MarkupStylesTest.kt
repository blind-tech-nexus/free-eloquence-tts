package com.blindtechnexus.freeeloquencetts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The emphasis a page carries in its markup, read out in words.
 *
 * This is the half of "read text styles" that a browser does not turn into
 * spans: a feed reader, an e-mail client and a reader that hands over the
 * markup rather than the rendered text all arrive as characters, and before
 * this the tags were stripped and the emphasis with them -- which is why a web
 * article read flat however loudly the switch was on.
 *
 * What is checked: that the emphasis is announced, that a tag which says
 * nothing about a word is dropped quietly, and that prose which happens to
 * contain a bracket or an asterisk is left exactly as it was.
 */
class MarkupStylesTest {

	@Test
	fun boldAndItalicAreSaid() {
		assertEquals("bold sale end bold", MarkupStyles.annotate("<b>sale</b>"))
		assertEquals("bold sale end bold", MarkupStyles.annotate("<strong>sale</strong>"))
		assertEquals("italic sale end italic", MarkupStyles.annotate("<em>sale</em>"))
		assertEquals("strikethrough old end strikethrough", MarkupStyles.annotate("<s>old</s>"))
	}

	@Test
	fun inlineCssStylesAreSaid() {
		assertEquals(
			"bold red sale end red end bold",
			MarkupStyles.annotate("<span style=\"font-weight: bold; color: red\">sale</span>")
		)
		assertEquals(
			"underline old end underline",
			MarkupStyles.annotate("<span style=\"text-decoration: underline\">old</span>")
		)
	}

	@Test
	fun aHeadingIsAHeading() {
		assertEquals("heading 2 Today end heading", MarkupStyles.annotate("<h2>Today</h2>"))
	}

	@Test
	fun aLinkIsALink() {
		assertEquals("link Shop end link", MarkupStyles.annotate("<a href=\"/cart\">Shop</a>"))
	}

	@Test
	fun anImageIsItsAlternativeText() {
		assertEquals("image a cat on a mat", MarkupStyles.annotate("<img alt=\"a cat on a mat\">"))
		// An image with no alternative text is still an image.
		assertEquals("image", MarkupStyles.annotate("<img src=\"x.png\">"))
	}

	@Test
	fun aLayoutTagSaysNothingButIsABreak() {
		assertEquals("one two", MarkupStyles.annotate("<div>one</div><div>two</div>"))
		assertEquals("one two", MarkupStyles.annotate("one<br />two"))
		assertEquals("bullet One bullet Two", MarkupStyles.annotate("<ul><li>One</li><li>Two</li></ul>"))
	}

	@Test
	fun anUnclosedTagIsClosedInTheWords() {
		// A page that ends inside a <b> must not leave a reader waiting for
		// the end of something that never comes.
		assertEquals("bold sale end bold", MarkupStyles.annotate("<b>sale"))
	}

	@Test
	fun markdownMarksAreSaidTheSameWay() {
		assertEquals("bold sale end bold", MarkupStyles.annotate("**sale**"))
		assertEquals("italic sale end italic", MarkupStyles.annotate("*sale*"))
		assertEquals("bold sale end bold", MarkupStyles.annotate("__sale__"))
	}

	@Test
	fun anAsteriskThatIsNotAMarkIsLeftAlone() {
		// Arithmetic, and a footnote, are not emphasis.
		assertEquals("2 * 3", MarkupStyles.annotate("2 * 3"))
		assertEquals("a * b * c", MarkupStyles.annotate("a * b * c"))
	}

	@Test
	fun proseWithABracketIsUntouched() {
		val text = "if (a < b) then stop"
		assertSame(text, MarkupStyles.annotate(text))
	}

	@Test
	fun textWithNoMarkupIsTheSameString() {
		val text = "Nothing here but words."
		assertSame(text, MarkupStyles.annotate(text))
	}
}
