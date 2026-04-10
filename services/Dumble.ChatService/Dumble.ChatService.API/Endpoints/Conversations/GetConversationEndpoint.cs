using FastEndpoints;
using MediatR;
using Dumble.ChatService.Application.Features.Conversations.Queries.GetConversation;
using Dumble.ChatService.Contracts.Conversations;

namespace Dumble.ChatService.API.Endpoints.Conversations;

public class GetConversationEndpoint : EndpointWithoutRequest<ConversationResponse>
{
    private readonly IMediator _mediator;

    public GetConversationEndpoint(IMediator mediator)
    {
        _mediator = mediator;
    }

    public override void Configure()
    {
        Get("/api/chat/conversations/{id}");
        Claims("userId");
    }

    public override async Task HandleAsync(CancellationToken ct)
    {
        var id = Route<string>("id")!;
        var result = await _mediator.Send(new GetConversationQuery(id), ct);
        await SendAsync(result, cancellation: ct);
    }
}
