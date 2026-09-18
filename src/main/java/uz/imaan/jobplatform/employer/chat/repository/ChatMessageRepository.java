package uz.imaan.jobplatform.employer.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.imaan.jobplatform.employer.chat.entity.ChatMessage;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("SELECT c FROM ChatMessage c WHERE " +
            "(c.senderChatId = :user1 AND c.receiverChatId = :user2) OR " +
            "(c.senderChatId = :user2 AND c.receiverChatId = :user1) " +
            "ORDER BY c.createdAt ASC")
    List<ChatMessage> findConversation(@Param("user1") Long user1, @Param("user2") Long user2);

    long countByReceiverChatIdAndSenderChatIdAndIsReadFalse(Long receiverId, Long senderId);

    @Query("SELECT DISTINCT CASE WHEN c.senderChatId = :userId THEN c.receiverChatId ELSE c.senderChatId END " +
            "FROM ChatMessage c WHERE c.senderChatId = :userId OR c.receiverChatId = :userId")
    List<Long> findAllChatPartners(@Param("userId") Long userId);
}