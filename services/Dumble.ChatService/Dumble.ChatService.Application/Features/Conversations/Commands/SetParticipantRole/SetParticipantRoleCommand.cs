using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.SetParticipantRole;

public sealed record SetParticipantRoleCommand(
    string ConversationId,
    string CallerId,
    string TargetUserId,
    string Role
) : IRequest;
