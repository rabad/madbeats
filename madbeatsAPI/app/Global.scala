import play.api._
import play.api.mvc._
import play.filters.headers.SecurityHeadersFilter

/**
 * Created by dataclimber on 05/02/15.
 */

object Global extends WithFilters(SecurityHeadersFilter()) with GlobalSettings {
}