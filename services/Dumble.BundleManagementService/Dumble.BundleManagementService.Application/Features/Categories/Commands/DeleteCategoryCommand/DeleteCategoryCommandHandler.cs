using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Commands.DeleteCategoryCommand;

public sealed class DeleteCategoryCommandHandler(
    IGenericRepository<Category, CategoryId> categoryRepository,
    ILoggedInUserService loggedInUserService) : IRequestHandler<DeleteCategoryCommand>
{
    public async Task Handle(DeleteCategoryCommand request, CancellationToken cancellationToken)
    {
        var user = loggedInUserService.GetCurrentUser();
        if (!user.IsInRole(UserType.Admin))
            throw new UnauthorizedAccessException("Only administrators can delete categories");

        var category = await categoryRepository.Get(CategoryId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Category {request.Id} not found");

        categoryRepository.Delete(category);
        await categoryRepository.CompleteAsync();
    }
}
