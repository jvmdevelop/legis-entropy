package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.dto.CommentDTO;
import com.jvmd.graphsservice.dto.UpsertCommentRequest;
import com.jvmd.graphsservice.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-graphs/{graphId}/comments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentDTO> create(
            @PathVariable String graphId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody UpsertCommentRequest req) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.ok(commentService.upsert(graphId, uid, req));
    }

    @GetMapping
    public List<CommentDTO> list(@PathVariable String graphId) {
        return commentService.listByGraph(graphId);
    }

    @GetMapping("/{commentId}")
    public ResponseEntity<CommentDTO> get(@PathVariable String graphId, @PathVariable String commentId) {
        return commentService.get(commentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable String graphId, @PathVariable String commentId) {
        commentService.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}
