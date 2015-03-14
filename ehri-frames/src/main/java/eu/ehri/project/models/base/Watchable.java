package eu.ehri.project.models.base;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.javahandler.JavaHandler;
import com.tinkerpop.frames.modules.javahandler.JavaHandlerContext;
import eu.ehri.project.models.UserProfile;
import eu.ehri.project.models.annotations.Meta;

import static eu.ehri.project.definitions.Ontology.USER_WATCHING_ITEM;

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
public interface Watchable extends AccessibleEntity {
    String WATCHED_COUNT = "watchedBy";

    @Adjacency(label = USER_WATCHING_ITEM, direction = Direction.IN)
    public Iterable<UserProfile> getWatchers();

    @Meta(WATCHED_COUNT)
    @JavaHandler
    public long getWatchedCount();

    abstract class Impl implements JavaHandlerContext<Vertex>, Watchable {

        @Override
        public long getWatchedCount() {
            return gremlin().inE(USER_WATCHING_ITEM).count();
        }
    }
}
