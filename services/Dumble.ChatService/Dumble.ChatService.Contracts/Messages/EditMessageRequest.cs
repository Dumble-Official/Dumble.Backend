using System.ComponentModel.DataAnnotations;

namespace Dumble.ChatService.Contracts.Messages;

// Length cap mirrors the EditMessageCommandHandler's MaxContentLength so the
// validator rejects oversize edits before MediatR dispatch instead of
// throwing ArgumentException out of the handler.
public record EditMessageRequest([Required(AllowEmptyStrings = false), MaxLength(10000)] string Content);
