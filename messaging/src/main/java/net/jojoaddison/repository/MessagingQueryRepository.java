package net.jojoaddison.repository;

import java.util.List;
import java.util.Optional;
import net.jojoaddison.domain.Conversation;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Lookups behind the Messages screen and the bell menu. */
@Repository
public interface MessagingQueryRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findByReference(String reference);

    /** A customer sees their own threads; a professional sees the threads addressed to their ref. */
    @Query("select c from Conversation c where c.customerLogin = :login or c.professionalRef = :professionalRef order by c.lastMessageAt desc nulls last")
    List<Conversation> findVisibleTo(@Param("login") String login, @Param("professionalRef") String professionalRef);

    @Query("select m from Message m where m.conversation.id = :conversationId order by m.sentAt asc")
    List<Message> findMessages(@Param("conversationId") Long conversationId);

    @Query("select n from Notification n where n.recipientLogin = :login order by n.raisedAt desc")
    List<Notification> findNotifications(@Param("login") String login);

    @Query("select count(n) from Notification n where n.recipientLogin = :login and n.readAt is null")
    long countUnread(@Param("login") String login);
}
