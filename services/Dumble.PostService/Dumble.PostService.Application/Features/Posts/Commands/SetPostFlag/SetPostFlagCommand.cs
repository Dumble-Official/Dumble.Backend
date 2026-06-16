using MediatR;

namespace Dumble.PostService.Application.Features.Posts.Commands.SetPostFlag;

/// <summary>Flag (or unflag) a post for moderation. Moderator/Admin only.</summary>
public record SetPostFlagCommand(Guid PostId, bool Flagged) : IRequest;
