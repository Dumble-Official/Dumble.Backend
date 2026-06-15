using MediatR;

namespace Dumble.PostService.Application.Features.Comments.Commands.SetCommentFlag;

/// <summary>Flag (or unflag) a comment for moderation. Moderator/Admin only.</summary>
public record SetCommentFlagCommand(Guid CommentId, bool Flagged) : IRequest;
