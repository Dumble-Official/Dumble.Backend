using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;

public sealed record UpdateBundleCommand(
    Guid Id,
    string? Name,
    string? Description,
    decimal? Price,
    string? Status,
    DateTime? ExpiresOn,
    Guid? CategoryId
) : IRequest;
