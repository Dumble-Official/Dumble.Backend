using MediatR;

namespace Dumble.ChatService.Application.Features.Conversations.Commands.RemoveParticipant;

public sealed record RemoveParticipantCommand(
    string ConversationId,
    string CallerId,
    string TargetUserId
) : IRequest;
