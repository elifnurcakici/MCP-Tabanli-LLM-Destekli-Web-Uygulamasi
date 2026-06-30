package com.elif.mcpproject.chat;

import com.elif.mcpproject.ai.AiMcpClient;
import com.elif.mcpproject.chat.dto.CitationResponse;
import com.elif.mcpproject.chat.dto.ChatResponse;
import com.elif.mcpproject.chat.dto.MessageResponse;
import com.elif.mcpproject.chat.dto.MessageRequest;
import com.elif.mcpproject.chat.dto.SendMessageResponse;
import com.elif.mcpproject.document.Document;
import com.elif.mcpproject.document.DocumentRepository;
import com.elif.mcpproject.security.CurrentUserService;
import com.elif.mcpproject.user.AppUser;
import lombok.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Getter @Setter @Builder
public class ChatService {
    private static final Pattern SOURCE_TAG_PATTERN = Pattern.compile(
            "\\s*\\[source:\\s*\\d+\\s+page:\\s*[^\\]]+\\]\\s*[,;]?\\s*",
            Pattern.CASE_INSENSITIVE
    );

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final CurrentUserService currentUserService;
    private final MessageCryptoService messageCryptoService;

    private final AiMcpClient aiMcpClient;

    @Transactional
    public ChatResponse createChat(Long documentId,String title, Principal principal) {
        AppUser user = currentUserService.requireCurrentUser(principal);

        if (documentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentId is required");
        }

        Document doc = documentRepository.findByIdAndUserId(documentId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        String safeTitle = (title == null || title.isBlank()) ? null : title.trim();

        Chat chat = Chat.builder()
                .user(user)
                .document(doc)
                .title(safeTitle)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        Chat saved = chatRepository.save(chat);
        return new ChatResponse(
                saved.getId(),
                saved.getDocument() == null ? null : saved.getDocument().getId(),
                saved.getTitle(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<ChatResponse> listMyChatsPaged(Pageable pageable, Principal principal) {
        AppUser user = currentUserService.requireCurrentUser(principal);

        return chatRepository
                .findAllByUserIdOrderByUpdatedAtDesc(user.getId(), pageable)
                .map(c -> new ChatResponse(
                        c.getId(),
                        c.getDocument() == null ? null : c.getDocument().getId(),
                        c.getTitle(),
                        c.getCreatedAt(),
                        c.getUpdatedAt()
                ));
    }

    @Transactional
    public void deleteChat(Long chatId, Principal principal) {
        AppUser user = currentUserService.requireCurrentUser(principal);
        Chat chat = chatRepository.findByIdAndUserId(chatId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));

        messageRepository.deleteAllByChatId(chat.getId());
        chatRepository.delete(chat);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> listMessagesPaged(Long chatId, Pageable pageable, Principal principal) {
        AppUser user = currentUserService.requireCurrentUser(principal);
        Chat chat = chatRepository.findByIdAndUserId(chatId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));

        return messageRepository.findAllByChatIdOrderByCreatedAtAsc(chat.getId(), pageable)
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getRole().name(),
                        m.getStatus().name(),
                        responseContent(m),
                        m.getCreatedAt()
                ));
    }


    @Transactional
    public SendMessageResponse sendUserMessage(Long chatId, MessageRequest req, Principal principal) {
        if (req == null || req.content() == null || req.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }

        AppUser user = currentUserService.requireCurrentUser(principal);

        Chat chat = chatRepository.findByIdAndUserId(chatId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));

        if (chat.getDocument() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat must be linked to a document for RAG answer");
        }

        String content = req.content().trim();
        String clientMessageId = (req.clientMessageId() == null || req.clientMessageId().isBlank())
                ? null
                : req.clientMessageId().trim();

        if (clientMessageId != null) {
            var existing = messageRepository.findByClientMessageId(clientMessageId);
            if (existing.isPresent()) {
                Message userMsg = existing.get();
                // Güvenlik: aynı chat mi? (clientMessageId global unique olsa bile)
                if (!userMsg.getChat().getId().equals(chat.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "clientMessageId conflict");
                }

                Message assistantMSg = messageRepository
                        .findFirstByChatIdAndRoleOrderByCreatedAtDesc(chat.getId(), Message.Role.ASSISTANT)
                        .orElse(null);

                return new SendMessageResponse(
                        toResponse(userMsg),
                        assistantMSg == null ? null : toResponse(assistantMSg),
                        List.of()
                );


            }
        }

        if (chat.getTitle() == null || chat.getTitle().isBlank()) {
            chat.setTitle(buildChatTitle(content));
            chatRepository.save(chat);
        }

        Message userMsg = Message.builder()
                .chat(chat)
                .role(Message.Role.USER)
                .status(Message.Status.CREATED) // sende farklıysa uyarlarsın
                .clientMessageId(clientMessageId)
                .content(messageCryptoService.encrypt(content))
                .createdAt(Instant.now())
                .build();

        Message savedUser;

        try {
            savedUser = messageRepository.save(userMsg);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (clientMessageId != null) {
                Message m = messageRepository.findByClientMessageId(clientMessageId)
                        .orElseThrow(() -> e);

                if (!m.getChat().getId().equals(chat.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "clientMessageId conflict");
                }

                Message assistantMsg = messageRepository
                        .findFirstByChatIdAndRoleOrderByCreatedAtDesc(chat.getId(), Message.Role.ASSISTANT)
                        .orElse(null);

                return new SendMessageResponse(
                        toResponse(m),
                        assistantMsg == null ? null : toResponse(assistantMsg),
                        List.of()
                );
            }
            throw e;
        }

        AiMcpClient.AnswerResult answerResult = aiMcpClient.answer(chat.getDocument().getId(), content, 3);

        Message assistantMsg = Message.builder()
                .chat(chat)
                .role(Message.Role.ASSISTANT)
                .status(Message.Status.CREATED)
                .content(messageCryptoService.encrypt(cleanAssistantAnswer(answerResult.answer())))
                .createdAt(Instant.now())
                .build();

        Message savedAssistant = messageRepository.save(assistantMsg);

        return new SendMessageResponse(
                toResponse(savedUser),
                toResponse(savedAssistant),
                toCitationResponses(answerResult.citations())
        );
    }

    private MessageResponse toResponse(Message m){
        return new MessageResponse(
                m.getId(),
                m.getRole().name(),
                m.getStatus().name(),
                responseContent(m),
                m.getCreatedAt()
        );
    }

    private String responseContent(Message message) {
        String content = messageCryptoService.decrypt(message.getContent());
        return message.getRole() == Message.Role.ASSISTANT
                ? cleanAssistantAnswer(content)
                : content;
    }

    private List<CitationResponse> toCitationResponses(List<AiMcpClient.Citation> citations) {
        if (citations == null) {
            return List.of();
        }

        Map<String, CitationResponse> uniqueByPage = new LinkedHashMap<>();

        for (AiMcpClient.Citation citation : citations) {
            uniqueByPage.putIfAbsent(pageKey(citation.pageStart(), citation.pageEnd()), new CitationResponse(
                        citation.id(),
                        citation.chunkIndex(),
                        citation.pageStart(),
                        citation.pageEnd(),
                        citation.startOffset(),
                        citation.endOffset(),
                        citation.score(),
                        ""
            ));
        }

        return List.copyOf(uniqueByPage.values());
    }

    private String pageKey(Integer pageStart, Integer pageEnd) {
        return (pageStart == null ? "?" : pageStart) + "-" + (pageEnd == null ? "?" : pageEnd);
    }

    private String cleanAssistantAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return "";
        }

        return SOURCE_TAG_PATTERN.matcher(answer)
                .replaceAll(" ")
                .replaceAll("\\s+([,.!?;:])", "$1")
                .replaceAll("([,;])\\s*$", "")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }

    private String buildChatTitle(String firstQuestion) {
        if (firstQuestion == null || firstQuestion.isBlank()) {
            return "Yeni sohbet";
        }

        Set<String> stopWords = Set.of(
                "bu", "şu", "su", "bir", "bana", "lütfen", "lutfen", "acaba",
                "mısın", "misin", "musun", "müsün", "miyim", "miyiz", "mi", "mı", "mu", "mü",
                "ne", "nedir", "nelerdir", "hakkında", "hakkinda", "kısaca", "kisaca",
                "açıklar", "aciklar", "açıkla", "acikla", "anlatır", "anlatir", "anlat",
                "söyler", "soyler", "özetler", "ozetler", "eder", "edebilir"
        );

        List<String> titleWords = new ArrayList<>();
        String normalized = firstQuestion
                .replaceAll("[!?.,;:()\\[\\]{}\"']", " ")
                .replaceAll("\\s+", " ")
                .trim();

        for (String word : normalized.split(" ")) {
            String lower = word.toLowerCase(Locale.forLanguageTag("tr-TR"));
            if (lower.length() < 2 || stopWords.contains(lower)) {
                continue;
            }

            titleWords.add(word);
            if (titleWords.size() == 6) {
                break;
            }
        }

        String title = titleWords.isEmpty() ? normalized : String.join(" ", titleWords);
        title = title.replaceAll("\\s+", " ").trim();

        if (title.length() > 60) {
            title = title.substring(0, 60).replaceAll("\\s+\\S*$", "").trim();
        }

        if (title.isBlank()) {
            return "Yeni sohbet";
        }

        return title.substring(0, 1).toUpperCase(Locale.forLanguageTag("tr-TR")) + title.substring(1);
    }
}
