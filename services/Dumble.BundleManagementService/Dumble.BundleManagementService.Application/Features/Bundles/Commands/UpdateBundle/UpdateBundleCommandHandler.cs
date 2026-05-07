using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using MediatR;
using Name = Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects.Name;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;

internal sealed class UpdateBundleCommandHandler(
    IGenericRepository<Bundle, BundleId> bundlesRepository,
    ILoggedInUserService loggedInUserService) : IRequestHandler<UpdateBundleCommand>
{
    public async Task Handle(UpdateBundleCommand request, CancellationToken cancellationToken)
    {
        var loggedInUser = loggedInUserService.GetCurrentUser();

        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        var modifier = AccountId.Create(AccountIdentity.ToAccountGuid(loggedInUser.Id));
        bundle.AssertOwnedBy(modifier);

        Status? status = request.Status is null ? null : Enum.Parse<Status>(request.Status, ignoreCase: true);

        bundle.Modify(
            modifier: modifier,
            name: request.Name is null ? null : Name.Create(request.Name),
            description: request.Description is null ? null : Description.Create(request.Description),
            price: request.Price is null ? null : Price.Create(request.Price.Value),
            status: status,
            expiresOn: request.ExpiresOn,
            categoryId: request.CategoryId is null ? null : CategoryId.Create(request.CategoryId.Value));

        bundlesRepository.Update(bundle);

        var rowsAffected = await bundlesRepository.CompleteAsync();
    }
}