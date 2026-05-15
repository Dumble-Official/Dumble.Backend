using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.CreateBundle;

public sealed record CreateBundleCommand(
    IReadOnlyList<UploadedImage>? Images,
    string Name,
    string Description,
    decimal Price,
    string Status,
    DateTime ExpiresOn,
    Guid CategoryId
) : IRequest<BundleId>;
