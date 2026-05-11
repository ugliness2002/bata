package bata.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RoomTest {

    @Test
    void fromFrame() {
        String frame = "BAT_MAPPER;;valley of the kings;;$apr1$dF!!_X#W$oLY.db0nn2vyavS8dDcTV/;;east;;1;;The lower deck;;This rectangular room is the entry point to the lower deck. The room is\n" +
                "gloomy. There is a nice carpet in the northeast corner. A big sofa stands on\n" +
                "the south wall.  A standing desk is put in front of the door to the west. The\n" +
                "walls are covered with tapestries. A stairway leads up.\n" +
                ";;west,up;;";
        Room room = Room.fromFrame(frame);

        assertThat(room).isNotNull();
        assertThat(room.getName()).isEqualTo("The lower deck");
        assertThat(room.getArea()).isEqualTo("valley of the kings");
    }

    @Test
    void fromFrame_noExits() {
        String frame = "BAT_MAPPER;;valley of the kings;;$apr1$dF!!_X#W$5HBKmsQpfL0kQhBz/8VvC.;;west;;1;;A spacious cabin;;A big bed is standing on the east wall, next to it a nightstand. Opposite of\n" +
                "the entrance, right under the scuttles, there is a huge desk with a carpet in\n" +
                "front of it. A washstand stands lonely in the southwest corner. South of the\n" +
                "door is a big closet.\n" +
                ";;;;";
        Room room = Room.fromFrame(frame);

        assertThat(room).isNotNull();
        assertThat(room.getExits()).isEqualTo("");
    }
}